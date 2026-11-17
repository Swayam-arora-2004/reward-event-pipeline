package com.swayam.rewards.exception;

/** The event could not be written to Kafka. Mapped to HTTP 503. */
public class PublishFailedException extends RuntimeException {

    public PublishFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
