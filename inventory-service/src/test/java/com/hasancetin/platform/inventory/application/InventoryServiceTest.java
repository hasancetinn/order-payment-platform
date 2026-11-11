package com.hasancetin.platform.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.hasancetin.platform.common.events.DomainEvent;
import com.hasancetin.platform.common.events.InventoryEvents.StockRejected;
import com.hasancetin.platform.common.events.InventoryEvents.StockReserved;
import com.hasancetin.platform.common.events.OrderEvents.OrderCreated;
import com.hasancetin.platform.common.events.OrderLine;
import com.hasancetin.platform.common.events.PaymentEvents.PaymentFailed;
import com.hasancetin.platform.common.events.Topics;
import com.hasancetin.platform.common.idempotency.IdempotencyGuard;
import com.hasancetin.platform.common.outbox.OutboxService;
import com.hasancetin.platform.inventory.domain.Product;
import com.hasancetin.platform.inventory.domain.ProductRepository;
import com.hasancetin.platform.inventory.domain.ReservationStatus;
import com.hasancetin.platform.inventory.domain.StockReservation;
import com.hasancetin.platform.inventory.domain.StockReservationRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InventoryServiceTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final StockReservationRepository reservations = mock(StockReservationRepository.class);
    private final OutboxService outbox = mock(OutboxService.class);
    private final IdempotencyGuard idempotency = mock(IdempotencyGuard.class);
    private final InventoryService service = new InventoryService(products, reservations, outbox, idempotency);

    private final Product laptop = new Product("SKU-LAPTOP", "Laptop", new BigDecimal("1000.00"), 5);
    private final Product watch = new Product("SKU-WATCH", "Watch", new BigDecimal("500.00"), 1);

    @BeforeEach
    void setUp() {
        when(idempotency.markProcessed(any())).thenReturn(true);
        when(products.lockBySkuIn(any())).thenAnswer(inv -> {
            Collection<String> skus = inv.getArgument(0);
            return List.of(laptop, watch).stream().filter(p -> skus.contains(p.getSku())).toList();
        });
    }

    @Test
    void reservesStockAndPublishesStockReserved() {
        UUID orderId = UUID.randomUUID();

        service.handleOrderCreated(order(orderId, line("SKU-LAPTOP", 2), line("SKU-WATCH", 1)));

        assertThat(laptop.getAvailableQuantity()).isEqualTo(3);
        assertThat(watch.getAvailableQuantity()).isZero();
        verify(reservations, org.mockito.Mockito.times(2)).save(any(StockReservation.class));
        verify(outbox).publish(eq(Topics.INVENTORY_EVENTS), any(StockReserved.class));
    }

    @Test
    void rejectsWholeOrderIfOneLineCannotBeFulfilledAndTouchesNoStock() {
        UUID orderId = UUID.randomUUID();

        service.handleOrderCreated(order(orderId, line("SKU-LAPTOP", 1), line("SKU-WATCH", 3)));

        assertThat(laptop.getAvailableQuantity()).isEqualTo(5);
        assertThat(watch.getAvailableQuantity()).isEqualTo(1);
        verify(reservations, never()).save(any());
        ArgumentCaptor<DomainEvent> event = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox).publish(eq(Topics.INVENTORY_EVENTS), event.capture());
        assertThat(event.getValue()).isInstanceOf(StockRejected.class);
        assertThat(((StockRejected) event.getValue()).reason()).contains("SKU-WATCH");
    }

    @Test
    void rejectsUnknownSku() {
        service.handleOrderCreated(order(UUID.randomUUID(), line("SKU-UNKNOWN", 1)));

        verify(outbox).publish(eq(Topics.INVENTORY_EVENTS), any(StockRejected.class));
    }

    @Test
    void duplicateOrderCreatedIsIgnored() {
        when(idempotency.markProcessed(any())).thenReturn(false);

        service.handleOrderCreated(order(UUID.randomUUID(), line("SKU-LAPTOP", 1)));

        assertThat(laptop.getAvailableQuantity()).isEqualTo(5);
        verify(outbox, never()).publish(any(), any());
    }

    @Test
    void paymentFailureReleasesReservedStock() {
        UUID orderId = UUID.randomUUID();
        laptop.reserve(2);
        StockReservation reservation = new StockReservation(orderId, "SKU-LAPTOP", 2);
        when(reservations.findByOrderId(orderId)).thenReturn(List.of(reservation));

        service.handlePaymentFailed(PaymentFailed.of(orderId, "declined"));

        assertThat(laptop.getAvailableQuantity()).isEqualTo(5);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }

    private static OrderLine line(String sku, int quantity) {
        return new OrderLine(sku, quantity, new BigDecimal("10.00"));
    }

    private static OrderCreated order(UUID orderId, OrderLine... lines) {
        return OrderCreated.of(orderId, "user-1", List.of(lines), new BigDecimal("100.00"), "EUR");
    }
}
