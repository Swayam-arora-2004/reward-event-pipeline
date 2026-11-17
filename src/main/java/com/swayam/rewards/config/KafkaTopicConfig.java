package com.swayam.rewards.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Spring's KafkaAdmin creates these topics at startup if they do not exist yet. */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic purchasesTopic(RewardsProperties properties) {
        return TopicBuilder.name(properties.topics().purchases())
                .partitions(properties.topics().partitions())
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic purchasesDeadLetterTopic(RewardsProperties properties) {
        return TopicBuilder.name(properties.topics().purchasesDlt())
                .partitions(properties.topics().partitions())
                .replicas(1)
                .build();
    }
}
