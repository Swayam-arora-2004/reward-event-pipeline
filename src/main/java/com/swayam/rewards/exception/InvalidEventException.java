package com.swayam.rewards.exception;

/** A consumed message is unreadable or breaks the validation rules. Retried, then dead-lettered. */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
