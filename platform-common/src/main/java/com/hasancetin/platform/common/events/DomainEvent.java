package com.hasancetin.platform.common.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Contract shared by every event that travels over Kafka.
 * <p>
 * {@link #eventId()} is the idempotency key used by consumers, {@link #aggregateId()} becomes the Kafka
 * message key so that all events of one aggregate (e.g. one order) stay ordered on one partition.
 */
public interface DomainEvent {

    UUID eventId();

    Instant occurredAt();

    String aggregateId();

    default String eventType() {
        return getClass().getSimpleName();
    }
}
