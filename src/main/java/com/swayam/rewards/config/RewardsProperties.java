package com.swayam.rewards.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Every tunable value of the app, bound from the "rewards" section of application.yml. */
@Validated
@ConfigurationProperties(prefix = "rewards")
public record RewardsProperties(
        @Valid Topics topics,
        @Valid Kafka kafka,
        @Valid Retry retry,
        @Valid Points points) {

    public record Topics(@NotBlank String purchases, @NotBlank String purchasesDlt, @Positive int partitions) {
    }

    public record Kafka(@NotBlank String consumerGroup, @Positive long sendTimeoutMs) {
    }

    public record Retry(@Positive int maxAttempts, @Positive long backoffMs) {
    }

    public record Points(@Positive long rupeesPerPoint) {
    }
}
