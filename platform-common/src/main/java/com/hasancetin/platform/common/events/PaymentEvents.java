package com.hasancetin.platform.common.events;

import java.time.Instant;
import java.util.UUID;

/** Events published by payment-service on {@link Topics#PAYMENT_EVENTS}. */
public final class PaymentEvents {

    private PaymentEvents() {
    }

    public record PaymentCompleted(UUID eventId, Instant occurredAt, UUID orderId, UUID paymentId)
            implements DomainEvent {

        public static PaymentCompleted of(UUID orderId, UUID paymentId) {
            return new PaymentCompleted(UUID.randomUUID(), Instant.now(), orderId, paymentId);
        }

        @Override
        public String aggregateId() {
            return orderId.toString();
        }
    }

    /** Triggers compensation: stock is released and the order is cancelled. */
    public record PaymentFailed(UUID eventId, Instant occurredAt, UUID orderId, String reason)
            implements DomainEvent {

        public static PaymentFailed of(UUID orderId, String reason) {
            return new PaymentFailed(UUID.randomUUID(), Instant.now(), orderId, reason);
        }

        @Override
        public String aggregateId() {
            return orderId.toString();
        }
    }
}
