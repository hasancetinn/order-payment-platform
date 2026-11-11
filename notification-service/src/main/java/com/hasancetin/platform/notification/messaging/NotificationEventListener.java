package com.hasancetin.platform.notification.messaging;

import com.hasancetin.platform.common.events.AuthEvents.UserRegistered;
import com.hasancetin.platform.common.events.OrderEvents.OrderCancelled;
import com.hasancetin.platform.common.events.OrderEvents.OrderConfirmed;
import com.hasancetin.platform.common.events.Topics;
import com.hasancetin.platform.common.messaging.EventHeaders;
import com.hasancetin.platform.common.messaging.EventSerde;
import com.hasancetin.platform.notification.application.NotificationService;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notificationService;
    private final EventSerde serde;

    public NotificationEventListener(NotificationService notificationService, EventSerde serde) {
        this.notificationService = notificationService;
        this.serde = serde;
    }

    @KafkaListener(topics = {Topics.AUTH_EVENTS, Topics.ORDER_EVENTS})
    public void onEvent(ConsumerRecord<String, String> record) {
        String type = EventHeaders.eventType(record);
        switch (type) {
            case "UserRegistered" -> notificationService.handleUserRegistered(serde.fromJson(record.value(), UserRegistered.class));
            case "OrderConfirmed" -> notificationService.handleOrderConfirmed(serde.fromJson(record.value(), OrderConfirmed.class));
            case "OrderCancelled" -> notificationService.handleOrderCancelled(serde.fromJson(record.value(), OrderCancelled.class));
            default -> log.debug("Ignoring event type '{}' from {}", type, record.topic());
        }
    }
}
