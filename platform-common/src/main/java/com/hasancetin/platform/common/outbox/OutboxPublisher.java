package com.hasancetin.platform.common.outbox;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.hasancetin.platform.common.messaging.EventHeaders;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relays pending outbox rows to Kafka (polling publisher). Delivery is at-least-once: if the process dies after
 * Kafka acknowledged a record but before the row is marked as SENT, the record is sent again, which is why every
 * consumer is idempotent.
 */
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int batchSize;

    public OutboxPublisher(OutboxRepository repository, KafkaTemplate<String, String> kafkaTemplate, int batchSize) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${platform.outbox.poll-interval-ms:500}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = repository.lockPending(batchSize);
        for (OutboxEvent event : batch) {
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(event.getTopic(), event.getMessageKey(), event.getPayload());
            record.headers().add(EventHeaders.EVENT_TYPE, event.getEventType().getBytes(StandardCharsets.UTF_8));
            record.headers().add(EventHeaders.EVENT_ID, event.getEventId().toString().getBytes(StandardCharsets.UTF_8));
            try {
                kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
                event.markSent();
                log.debug("Published {} {} to {}", event.getEventType(), event.getEventId(), event.getTopic());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException | TimeoutException e) {
                // Stop at the first failure to keep per-aggregate ordering; the rest is retried on the next tick.
                log.warn("Kafka publish failed for outbox event {} ({}), will retry: {}",
                        event.getEventId(), event.getEventType(), e.getMessage());
                return;
            }
        }
    }
}
