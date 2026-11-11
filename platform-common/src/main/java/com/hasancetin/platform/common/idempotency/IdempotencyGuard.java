package com.hasancetin.platform.common.idempotency;

import java.util.UUID;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent consumer helper. A handler calls {@link #markProcessed(UUID)} first, inside the same transaction that
 * applies the business change: if the transaction rolls back, the marker rolls back with it, so a redelivery is
 * processed again; if it commits, every further redelivery is skipped.
 */
public class IdempotencyGuard {

    private final ProcessedEventRepository repository;

    public IdempotencyGuard(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    /** @return {@code true} if this is the first time the event is seen, {@code false} for a duplicate */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markProcessed(UUID eventId) {
        return repository.insertIfAbsent(eventId) == 1;
    }
}
