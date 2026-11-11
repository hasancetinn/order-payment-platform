package com.hasancetin.platform.common.messaging;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

public final class EventHeaders {

    public static final String EVENT_TYPE = "event-type";
    public static final String EVENT_ID = "event-id";

    private EventHeaders() {
    }

    /** Returns the logical event type of a record, or an empty string if the header is missing. */
    public static String eventType(ConsumerRecord<?, ?> record) {
        Header header = record.headers().lastHeader(EVENT_TYPE);
        return header == null ? "" : new String(header.value(), StandardCharsets.UTF_8);
    }
}
