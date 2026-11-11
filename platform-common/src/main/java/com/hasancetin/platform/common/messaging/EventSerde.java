package com.hasancetin.platform.common.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hasancetin.platform.common.events.DomainEvent;

/** JSON (de)serialization of {@link DomainEvent}s. Payloads are plain JSON text on the wire. */
public class EventSerde {

    private final ObjectMapper objectMapper;

    public EventSerde(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new EventSerializationException("Cannot serialize " + event.eventType(), e);
        }
    }

    public <T extends DomainEvent> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new EventSerializationException("Cannot deserialize " + type.getSimpleName(), e);
        }
    }
}
