package com.hasancetin.platform.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import com.hasancetin.platform.common.events.DomainEvent;
import com.hasancetin.platform.common.events.InventoryEvents.StockReserved;
import com.hasancetin.platform.common.events.PaymentEvents.PaymentCompleted;
import com.hasancetin.platform.common.events.PaymentEvents.PaymentFailed;
import com.hasancetin.platform.common.events.Topics;
import com.hasancetin.platform.common.idempotency.IdempotencyGuard;
import com.hasancetin.platform.common.outbox.OutboxService;
import com.hasancetin.platform.payment.domain.Payment;
import com.hasancetin.platform.payment.domain.PaymentRepository;
import com.hasancetin.platform.payment.domain.PaymentStatus;
import com.hasancetin.platform.payment.provider.ChargeResult;
import com.hasancetin.platform.payment.provider.PaymentGateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PaymentServiceTest {

    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final OutboxService outbox = mock(OutboxService.class);
    private final IdempotencyGuard idempotency = mock(IdempotencyGuard.class);
    private final PaymentService service = new PaymentService(payments, gateway, outbox, idempotency);

    private final UUID orderId = UUID.randomUUID();
    private final StockReserved event =
            StockReserved.of(orderId, "user-1", new BigDecimal("250.00"), "EUR");

    @BeforeEach
    void setUp() {
        when(idempotency.markProcessed(any())).thenReturn(true);
        when(payments.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void approvedChargeStoresPaymentAndPublishesPaymentCompleted() {
        when(gateway.charge(orderId, event.totalAmount(), "EUR")).thenReturn(ChargeResult.approved("ref-1"));

        service.handleStockReserved(event);

        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(payment.capture());
        assertThat(payment.getValue().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getValue().getProviderReference()).isEqualTo("ref-1");
        verify(outbox).publish(eq(Topics.PAYMENT_EVENTS), any(PaymentCompleted.class));
    }

    @Test
    void declinedChargeStoresFailedPaymentAndPublishesPaymentFailed() {
        when(gateway.charge(orderId, event.totalAmount(), "EUR")).thenReturn(ChargeResult.declined("card limit"));

        service.handleStockReserved(event);

        ArgumentCaptor<DomainEvent> published = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox).publish(eq(Topics.PAYMENT_EVENTS), published.capture());
        assertThat(published.getValue()).isInstanceOf(PaymentFailed.class);
        assertThat(((PaymentFailed) published.getValue()).reason()).isEqualTo("card limit");
    }

    @Test
    void duplicateEventNeverChargesAgain() {
        when(idempotency.markProcessed(any())).thenReturn(false);

        service.handleStockReserved(event);

        verify(gateway, never()).charge(any(), any(), any());
        verify(outbox, never()).publish(any(), any());
    }

    @Test
    void existingPaymentForOrderNeverChargesAgain() {
        when(payments.existsByOrderId(orderId)).thenReturn(true);

        service.handleStockReserved(event);

        verify(gateway, never()).charge(any(), any(), any());
        verify(outbox, never()).publish(any(), any());
    }
}
