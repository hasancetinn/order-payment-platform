package com.hasancetin.platform.payment.application;

import java.util.Optional;
import java.util.UUID;

import com.hasancetin.platform.common.events.InventoryEvents.StockReserved;
import com.hasancetin.platform.common.events.PaymentEvents.PaymentCompleted;
import com.hasancetin.platform.common.events.PaymentEvents.PaymentFailed;
import com.hasancetin.platform.common.events.Topics;
import com.hasancetin.platform.common.idempotency.IdempotencyGuard;
import com.hasancetin.platform.common.outbox.OutboxService;
import com.hasancetin.platform.payment.domain.Payment;
import com.hasancetin.platform.payment.domain.PaymentRepository;
import com.hasancetin.platform.payment.provider.ChargeResult;
import com.hasancetin.platform.payment.provider.PaymentGateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final OutboxService outbox;
    private final IdempotencyGuard idempotency;

    public PaymentService(PaymentRepository payments, PaymentGateway gateway, OutboxService outbox,
                          IdempotencyGuard idempotency) {
        this.payments = payments;
        this.gateway = gateway;
        this.outbox = outbox;
        this.idempotency = idempotency;
    }

    /**
     * Charges the customer once stock is reserved. The provider call happens inside the DB transaction, which is
     * acceptable for this scale; a production system would typically split it (authorise first, then record).
     */
    @Transactional
    public void handleStockReserved(StockReserved event) {
        if (!idempotency.markProcessed(event.eventId())) {
            return;
        }
        // second line of defence: never charge the same order twice, even for a differently-id'd event
        if (payments.existsByOrderId(event.orderId())) {
            log.info("Order {} already has a payment, ignoring", event.orderId());
            return;
        }

        ChargeResult result = gateway.charge(event.orderId(), event.totalAmount(), event.currency());
        if (result.approved()) {
            Payment payment = payments.save(Payment.completed(event.orderId(), event.userId(),
                    event.totalAmount(), event.currency(), result.reference()));
            outbox.publish(Topics.PAYMENT_EVENTS, PaymentCompleted.of(event.orderId(), payment.getId()));
            log.info("Payment {} completed for order {}", payment.getId(), event.orderId());
        } else {
            payments.save(Payment.failed(event.orderId(), event.userId(), event.totalAmount(),
                    event.currency(), result.failureReason()));
            outbox.publish(Topics.PAYMENT_EVENTS, PaymentFailed.of(event.orderId(), result.failureReason()));
            log.info("Payment failed for order {}: {}", event.orderId(), result.failureReason());
        }
    }

    @Transactional(readOnly = true)
    public Optional<Payment> findForUser(String userId, UUID orderId) {
        return payments.findByOrderId(orderId).filter(p -> p.getUserId().equals(userId));
    }
}
