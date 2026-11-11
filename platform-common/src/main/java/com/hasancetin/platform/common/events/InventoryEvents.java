package com.hasancetin.platform.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Events published by inventory-service on {@link Topics#INVENTORY_EVENTS}. */
public final class InventoryEvents {

    private InventoryEvents() {
    }

    /** Stock is reserved: payment-service reacts by charging the customer. */
    public record StockReserved(UUID eventId, Instant occurredAt, UUID orderId, String userId,
                                BigDecimal totalAmount, String currency) implements DomainEvent {

        public static StockReserved of(UUID orderId, String userId, BigDecimal totalAmount, String currency) {
            return new StockReserved(UUID.randomUUID(), Instant.now(), orderId, userId, totalAmount, currency);
        }

        @Override
        public String aggregateId() {
            return orderId.toString();
        }
    }

    /** Stock could not be reserved: order-service cancels the order. */
    public record StockRejected(UUID eventId, Instant occurredAt, UUID orderId, String reason)
            implements DomainEvent {

        public static StockRejected of(UUID orderId, String reason) {
            return new StockRejected(UUID.randomUUID(), Instant.now(), orderId, reason);
        }

        @Override
        public String aggregateId() {
            return orderId.toString();
        }
    }
}
