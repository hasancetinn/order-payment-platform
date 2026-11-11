package com.hasancetin.platform.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Events published by order-service on {@link Topics#ORDER_EVENTS}. */
public final class OrderEvents {

    private OrderEvents() {
    }

    /** Starts the saga: inventory-service reacts by reserving stock. */
    public record OrderCreated(UUID eventId, Instant occurredAt, UUID orderId, String userId,
                               List<OrderLine> items, BigDecimal totalAmount, String currency)
            implements DomainEvent {

        public static OrderCreated of(UUID orderId, String userId, List<OrderLine> items,
                                      BigDecimal totalAmount, String currency) {
            return new OrderCreated(UUID.randomUUID(), Instant.now(), orderId, userId, items, totalAmount, currency);
        }

        @Override
        public String aggregateId() {
            return orderId.toString();
        }
    }

    /** Saga finished successfully. */
    public record OrderConfirmed(UUID eventId, Instant occurredAt, UUID orderId, String userId,
                                 BigDecimal totalAmount, String currency) implements DomainEvent {

        public static OrderConfirmed of(UUID orderId, String userId, BigDecimal totalAmount, String currency) {
            return new OrderConfirmed(UUID.randomUUID(), Instant.now(), orderId, userId, totalAmount, currency);
        }

        @Override
        public String aggregateId() {
            return orderId.toString();
        }
    }

    /** Saga finished with a rollback (stock rejected or payment failed). */
    public record OrderCancelled(UUID eventId, Instant occurredAt, UUID orderId, String userId,
                                 String reason) implements DomainEvent {

        public static OrderCancelled of(UUID orderId, String userId, String reason) {
            return new OrderCancelled(UUID.randomUUID(), Instant.now(), orderId, userId, reason);
        }

        @Override
        public String aggregateId() {
            return orderId.toString();
        }
    }
}
