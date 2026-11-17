package com.swayam.rewards.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swayam.rewards.config.RewardsProperties;
import com.swayam.rewards.exception.PublishFailedException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publishes a purchase as a JSON string. The key is memberId so one member's events stay in order. */
@Component
public class PurchaseProducer {

    private static final Logger log = LoggerFactory.getLogger(PurchaseProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final RewardsProperties properties;

    public PurchaseProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
            RewardsProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /** Returns only after Kafka has acknowledged the write, so a 202 means the event is safely stored. */
    public void publish(PurchaseEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(properties.topics().purchases(), event.memberId(), json)
                    .get(properties.kafka().sendTimeoutMs(), TimeUnit.MILLISECONDS);
            log.info("Published event (eventId={}, memberId={})", event.eventId(), event.memberId());
        } catch (JsonProcessingException | ExecutionException | TimeoutException ex) {
            throw new PublishFailedException("Could not publish event " + event.eventId(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PublishFailedException("Interrupted while publishing event " + event.eventId(), ex);
        }
    }
}
