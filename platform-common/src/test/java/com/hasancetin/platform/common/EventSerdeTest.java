package com.hasancetin.platform.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hasancetin.platform.common.events.OrderEvents.OrderCreated;
import com.hasancetin.platform.common.events.OrderLine;
import com.hasancetin.platform.common.messaging.EventSerde;
import com.hasancetin.platform.common.messaging.EventSerializationException;

import org.junit.jupiter.api.Test;

class EventSerdeTest {

    private final EventSerde serde = new EventSerde(new ObjectMapper().findAndRegisterModules());

    @Test
    void roundTripKeepsAllFields() {
        UUID orderId = UUID.randomUUID();
        OrderCreated original = OrderCreated.of(orderId, "user-1",
                List.of(new OrderLine("SKU-1", 2, new BigDecimal("10.50"))), new BigDecimal("21.00"), "EUR");

        OrderCreated copy = serde.fromJson(serde.toJson(original), OrderCreated.class);

        assertThat(copy).isEqualTo(original);
        assertThat(copy.aggregateId()).isEqualTo(orderId.toString());
        assertThat(copy.eventType()).isEqualTo("OrderCreated");
    }

    @Test
    void malformedPayloadIsReportedAsSerializationFailure() {
        assertThatThrownBy(() -> serde.fromJson("{not json", OrderCreated.class))
                .isInstanceOf(EventSerializationException.class);
    }
}
