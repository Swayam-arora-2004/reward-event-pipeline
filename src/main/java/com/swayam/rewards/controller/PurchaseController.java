package com.swayam.rewards.controller;

import com.swayam.rewards.messaging.PurchaseEvent;
import com.swayam.rewards.messaging.PurchaseProducer;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PurchaseController {

    public record AcceptedResponse(String eventId, String status) {
    }

    private final PurchaseProducer producer;

    public PurchaseController(PurchaseProducer producer) {
        this.producer = producer;
    }

    @PostMapping("/purchases")
    public ResponseEntity<AcceptedResponse> accept(@Valid @RequestBody PurchaseEvent event) {
        producer.publish(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new AcceptedResponse(event.eventId(), "accepted"));
    }
}
