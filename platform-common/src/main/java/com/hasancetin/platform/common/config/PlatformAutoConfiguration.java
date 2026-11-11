package com.hasancetin.platform.common.config;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hasancetin.platform.common.events.Topics;
import com.hasancetin.platform.common.idempotency.IdempotencyGuard;
import com.hasancetin.platform.common.idempotency.ProcessedEventRepository;
import com.hasancetin.platform.common.messaging.EventSerde;
import com.hasancetin.platform.common.messaging.EventSerializationException;
import com.hasancetin.platform.common.outbox.OutboxPublisher;
import com.hasancetin.platform.common.outbox.OutboxRepository;
import com.hasancetin.platform.common.outbox.OutboxService;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Wires the cross-cutting messaging pieces for every service that has a database and talks to Kafka:
 * transactional outbox, idempotent-consumer guard, topic declaration and retry + dead-letter handling.
 * <p>
 * {@link AutoConfigurationPackage} makes the outbox / processed-event entities and repositories of this module
 * visible to the service's own JPA scanning.
 */
@AutoConfiguration(after = {KafkaAutoConfiguration.class, JacksonAutoConfiguration.class})
@AutoConfigurationPackage(basePackages = "com.hasancetin.platform.common")
@EnableScheduling
public class PlatformAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EventSerde eventSerde(ObjectMapper objectMapper) {
        return new EventSerde(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxService outboxService(OutboxRepository repository, EventSerde serde) {
        return new OutboxService(repository, serde);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxPublisher outboxPublisher(OutboxRepository repository,
                                           KafkaTemplate<String, String> kafkaTemplate,
                                           @Value("${platform.outbox.batch-size:100}") int batchSize) {
        return new OutboxPublisher(repository, kafkaTemplate, batchSize);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyGuard idempotencyGuard(ProcessedEventRepository repository) {
        return new IdempotencyGuard(repository);
    }

    @Bean
    public KafkaAdmin.NewTopics platformTopics(@Value("${platform.kafka.partitions:3}") int partitions,
                                               @Value("${platform.kafka.replicas:1}") short replicas) {
        List<NewTopic> topics = new ArrayList<>();
        for (String topic : Topics.ALL) {
            topics.add(TopicBuilder.name(topic).partitions(partitions).replicas(replicas).build());
            topics.add(TopicBuilder.name(topic + ".DLT").partitions(1).replicas(replicas).build());
        }
        return new KafkaAdmin.NewTopics(topics.toArray(NewTopic[]::new));
    }

    /**
     * Retries a failing record with exponential back-off (500ms, 1s, 2s) and then parks it on
     * {@code <topic>.DLT} so one bad message can never block a partition forever.
     */
    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1));
        ExponentialBackOff backOff = new ExponentialBackOff(500L, 2.0);
        backOff.setMaxAttempts(3);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(EventSerializationException.class);
        return handler;
    }
}
