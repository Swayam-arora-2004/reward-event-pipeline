package com.swayam.rewards.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swayam.rewards.exception.InvalidEventException;
import com.swayam.rewards.service.PointsService;
import jakarta.validation.Validator;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reads purchases from Kafka. Any exception thrown here is handled by the error handler
 * in KafkaErrorConfig: 3 attempts, then the message goes to the dead-letter topic.
 */
@Component
public class PurchaseConsumer {

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final PointsService pointsService;

    public PurchaseConsumer(ObjectMapper objectMapper, Validator validator, PointsService pointsService) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.pointsService = pointsService;
    }

    @KafkaListener(topics = "${rewards.topics.purchases}")
    public void onMessage(String payload) {
        pointsService.credit(parseAndValidate(payload));
    }

    private PurchaseEvent parseAndValidate(String payload) {
        PurchaseEvent event;
        try {
            event = objectMapper.readValue(payload, PurchaseEvent.class);
        } catch (JsonProcessingException ex) {
            // Do not log the payload or the parser message: both can echo user input (CWE-117).
            throw new InvalidEventException("Message is not a readable purchase event");
        }
        if (event == null || !validator.validate(event).isEmpty()) {
            throw new InvalidEventException("Purchase event breaks the validation rules");
        }
        return event;
    }
}
