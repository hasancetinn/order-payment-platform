package com.hasancetin.platform.order.messaging;

import com.hasancetin.platform.common.events.InventoryEvents.StockRejected;
import com.hasancetin.platform.common.events.PaymentEvents.PaymentCompleted;
import com.hasancetin.platform.common.events.PaymentEvents.PaymentFailed;
import com.hasancetin.platform.common.events.Topics;
import com.hasancetin.platform.common.messaging.EventHeaders;
import com.hasancetin.platform.common.messaging.EventSerde;
import com.hasancetin.platform.order.application.OrderService;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final OrderService orderService;
    private final EventSerde serde;

    public OrderEventListener(OrderService orderService, EventSerde serde) {
        this.orderService = orderService;
        this.serde = serde;
    }

    @KafkaListener(topics = {Topics.INVENTORY_EVENTS, Topics.PAYMENT_EVENTS})
    public void onEvent(ConsumerRecord<String, String> record) {
        String type = EventHeaders.eventType(record);
        switch (type) {
            case "StockRejected" -> orderService.handleStockRejected(serde.fromJson(record.value(), StockRejected.class));
            case "PaymentCompleted" -> orderService.handlePaymentCompleted(serde.fromJson(record.value(), PaymentCompleted.class));
            case "PaymentFailed" -> orderService.handlePaymentFailed(serde.fromJson(record.value(), PaymentFailed.class));
            default -> log.debug("Ignoring event type '{}' from {}", type, record.topic());
        }
    }
}
