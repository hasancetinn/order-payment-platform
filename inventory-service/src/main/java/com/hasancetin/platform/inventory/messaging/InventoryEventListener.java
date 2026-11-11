package com.hasancetin.platform.inventory.messaging;

import com.hasancetin.platform.common.events.OrderEvents.OrderCreated;
import com.hasancetin.platform.common.events.PaymentEvents.PaymentCompleted;
import com.hasancetin.platform.common.events.PaymentEvents.PaymentFailed;
import com.hasancetin.platform.common.events.Topics;
import com.hasancetin.platform.common.messaging.EventHeaders;
import com.hasancetin.platform.common.messaging.EventSerde;
import com.hasancetin.platform.inventory.application.InventoryService;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryEventListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventListener.class);

    private final InventoryService inventoryService;
    private final EventSerde serde;

    public InventoryEventListener(InventoryService inventoryService, EventSerde serde) {
        this.inventoryService = inventoryService;
        this.serde = serde;
    }

    @KafkaListener(topics = {Topics.ORDER_EVENTS, Topics.PAYMENT_EVENTS})
    public void onEvent(ConsumerRecord<String, String> record) {
        String type = EventHeaders.eventType(record);
        switch (type) {
            case "OrderCreated" -> inventoryService.handleOrderCreated(serde.fromJson(record.value(), OrderCreated.class));
            case "PaymentFailed" -> inventoryService.handlePaymentFailed(serde.fromJson(record.value(), PaymentFailed.class));
            case "PaymentCompleted" -> inventoryService.handlePaymentCompleted(serde.fromJson(record.value(), PaymentCompleted.class));
            default -> log.debug("Ignoring event type '{}' from {}", type, record.topic());
        }
    }
}
