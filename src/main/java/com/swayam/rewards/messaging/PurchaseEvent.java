package com.swayam.rewards.messaging;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * One purchase. Used as the HTTP request body and as the Kafka message value.
 * The id pattern keeps odd characters (such as line breaks) out of logs and the database.
 */
public record PurchaseEvent(
        @NotBlank @Size(max = PurchaseEvent.MAX_ID_LENGTH) @Pattern(regexp = PurchaseEvent.ID_REGEX) String eventId,
        @NotBlank @Size(max = PurchaseEvent.MAX_ID_LENGTH) @Pattern(regexp = PurchaseEvent.ID_REGEX) String memberId,
        @Positive @Max(PurchaseEvent.MAX_AMOUNT_RUPEES) long amountRupees) {

    public static final int MAX_ID_LENGTH = 64;
    public static final String ID_REGEX = "[A-Za-z0-9._-]+";
    /** Upper bound so a balance can never overflow a BIGINT (D-003). */
    public static final long MAX_AMOUNT_RUPEES = 1_000_000_000L;
}
