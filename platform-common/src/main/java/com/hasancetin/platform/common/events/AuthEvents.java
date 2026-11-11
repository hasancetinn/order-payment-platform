package com.hasancetin.platform.common.events;

import java.time.Instant;
import java.util.UUID;

/** Events published by auth-service on {@link Topics#AUTH_EVENTS}. */
public final class AuthEvents {

    private AuthEvents() {
    }

    public record UserRegistered(UUID eventId, Instant occurredAt, String userId, String email)
            implements DomainEvent {

        public static UserRegistered of(String userId, String email) {
            return new UserRegistered(UUID.randomUUID(), Instant.now(), userId, email);
        }

        @Override
        public String aggregateId() {
            return userId;
        }
    }
}
