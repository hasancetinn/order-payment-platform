package com.hasancetin.platform.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.hasancetin.platform.common.events.DomainEvent;
import com.hasancetin.platform.common.events.InventoryEvents.StockRejected;
import com.hasancetin.platform.common.events.OrderEvents.OrderCancelled;
import com.hasancetin.platform.common.events.OrderEvents.OrderConfirmed;
import com.hasancetin.platform.common.events.OrderEvents.OrderCreated;
import com.hasancetin.platform.common.events.OrderLine;
import com.hasancetin.platform.common.events.PaymentEvents.PaymentCompleted;
import com.hasancetin.platform.common.events.PaymentEvents.PaymentFailed;
import com.hasancetin.platform.common.events.Topics;
import com.hasancetin.platform.common.idempotency.IdempotencyGuard;
import com.hasancetin.platform.common.outbox.OutboxService;
import com.hasancetin.platform.order.api.CreateOrderRequest;
import com.hasancetin.platform.order.api.OrderResponse;
import com.hasancetin.platform.order.domain.Order;
import com.hasancetin.platform.order.domain.OrderRepository;
import com.hasancetin.platform.order.domain.OrderStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OrderServiceTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final OutboxService outbox = mock(OutboxService.class);
    private final IdempotencyGuard idempotency = mock(IdempotencyGuard.class);
    private final OrderService service = new OrderService(orders, outbox, idempotency);

    @BeforeEach
    void firstDeliveryByDefault() {
        when(idempotency.markProcessed(any())).thenReturn(true);
    }

    @Test
    void createOrderMergesLinesComputesTotalAndWritesOrderCreatedToOutbox() {
        var request = new CreateOrderRequest(List.of(
                new CreateOrderRequest.Item("SKU-1", 1, new BigDecimal("10.00")),
                new CreateOrderRequest.Item("SKU-1", 2, new BigDecimal("10.00")),
                new CreateOrderRequest.Item("SKU-2", 1, new BigDecimal("5.50"))), "EUR");

        OrderResponse response = service.createOrder("user-1", null, request);

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.totalAmount()).isEqualByComparingTo("35.50");
        assertThat(response.items()).hasSize(2);

        ArgumentCaptor<DomainEvent> event = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox).publish(eq(Topics.ORDER_EVENTS), event.capture());
        assertThat(event.getValue()).isInstanceOf(OrderCreated.class);
        OrderCreated created = (OrderCreated) event.getValue();
        assertThat(created.items()).containsExactly(
                new OrderLine("SKU-1", 3, new BigDecimal("10.00")),
                new OrderLine("SKU-2", 1, new BigDecimal("5.50")));
    }

    @Test
    void sameSkuWithDifferentPricesIsRejected() {
        var request = new CreateOrderRequest(List.of(
                new CreateOrderRequest.Item("SKU-1", 1, new BigDecimal("10.00")),
                new CreateOrderRequest.Item("SKU-1", 1, new BigDecimal("11.00"))), "EUR");

        assertThatThrownBy(() -> service.createOrder("user-1", null, request))
                .isInstanceOf(IllegalArgumentException.class);
        verify(outbox, never()).publish(any(), any());
    }

    @Test
    void replayedIdempotencyKeyReturnsExistingOrderWithoutNewEvent() {
        Order existing = pendingOrder();
        when(orders.findByUserIdAndIdempotencyKey("user-1", "key-1")).thenReturn(Optional.of(existing));

        OrderResponse response = service.createOrder("user-1", "key-1", new CreateOrderRequest(
                List.of(new CreateOrderRequest.Item("SKU-1", 1, BigDecimal.TEN)), "EUR"));

        assertThat(response.id()).isEqualTo(existing.getId());
        verify(orders, never()).save(any());
        verify(outbox, never()).publish(any(), any());
    }

    @Test
    void paymentCompletedConfirmsOrderAndPublishesOrderConfirmed() {
        Order order = pendingOrder();
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));

        service.handlePaymentCompleted(PaymentCompleted.of(order.getId(), UUID.randomUUID()));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(outbox).publish(eq(Topics.ORDER_EVENTS), any(OrderConfirmed.class));
    }

    @Test
    void paymentFailedCancelsOrder() {
        Order order = pendingOrder();
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));

        service.handlePaymentFailed(PaymentFailed.of(order.getId(), "declined"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancellationReason()).contains("declined");
        verify(outbox).publish(eq(Topics.ORDER_EVENTS), any(OrderCancelled.class));
    }

    @Test
    void stockRejectedCancelsOrder() {
        Order order = pendingOrder();
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));

        service.handleStockRejected(StockRejected.of(order.getId(), "out of stock"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(outbox).publish(eq(Topics.ORDER_EVENTS), any(OrderCancelled.class));
    }

    @Test
    void duplicateEventIsIgnored() {
        Order order = pendingOrder();
        when(idempotency.markProcessed(any())).thenReturn(false);

        service.handlePaymentCompleted(PaymentCompleted.of(order.getId(), UUID.randomUUID()));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orders, never()).findById(any());
        verify(outbox, never()).publish(any(), any());
    }

    @Test
    void lateFailureAfterConfirmationDoesNotCancelAConfirmedOrder() {
        Order order = pendingOrder();
        order.confirm();
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));

        service.handlePaymentFailed(PaymentFailed.of(order.getId(), "late"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(outbox, never()).publish(any(), any());
    }

    private static Order pendingOrder() {
        return Order.create("user-1", "EUR", null,
                List.of(new OrderLine("SKU-1", 1, new BigDecimal("10.00"))));
    }
}
