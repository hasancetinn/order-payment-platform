package com.hasancetin.platform.common.outbox;

import com.hasancetin.platform.common.events.DomainEvent;
import com.hasancetin.platform.common.messaging.EventSerde;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entry point for services: stores an event in the outbox table. It requires an already running transaction,
 * so the event can never be saved without the business change that caused it (and vice versa).
 */
public class OutboxService {

    private final OutboxRepository repository;
    private final EventSerde serde;

    public OutboxService(OutboxRepository repository, EventSerde serde) {
        this.repository = repository;
        this.serde = serde;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String topic, DomainEvent event) {
        repository.save(new OutboxEvent(event.eventId(), topic, event.aggregateId(),
                event.eventType(), serde.toJson(event)));
    }
}
