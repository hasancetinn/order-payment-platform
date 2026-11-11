package com.hasancetin.platform.payment.messaging;

import com.hasancetin.platform.common.events.InventoryEvents.StockReserved;
import com.hasancetin.platform.common.events.Topics;
import com.hasancetin.platform.common.messaging.EventHeaders;
import com.hasancetin.platform.common.messaging.EventSerde;
import com.hasancetin.platform.payment.application.PaymentService;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final PaymentService paymentService;
    private final EventSerde serde;

    public PaymentEventListener(PaymentService paymentService, EventSerde serde) {
        this.paymentService = paymentService;
        this.serde = serde;
    }

    @KafkaListener(topics = Topics.INVENTORY_EVENTS)
    public void onEvent(ConsumerRecord<String, String> record) {
        String type = EventHeaders.eventType(record);
        if ("StockReserved".equals(type)) {
            paymentService.handleStockReserved(serde.fromJson(record.value(), StockReserved.class));
        } else {
            log.debug("Ignoring event type '{}' from {}", type, record.topic());
        }
    }
}
