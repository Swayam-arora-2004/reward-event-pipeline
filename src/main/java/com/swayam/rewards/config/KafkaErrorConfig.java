package com.swayam.rewards.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Retry then dead-letter. Spring Boot picks up the CommonErrorHandler bean and
 * applies it to every @KafkaListener automatically.
 */
@Configuration
public class KafkaErrorConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorConfig.class);

    /** Partition -1 lets Kafka choose, so the DLT may have a different partition count (D-005). */
    private static final int ANY_PARTITION = -1;

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template, RewardsProperties properties) {
        String dltTopic = properties.topics().purchasesDlt();
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                template, (record, exception) -> new TopicPartition(dltTopic, ANY_PARTITION));

        // FixedBackOff counts retries after the first try: 3 attempts in total = 2 retries.
        long retries = properties.retry().maxAttempts() - 1L;
        DefaultErrorHandler handler = new DefaultErrorHandler(
                recoverer, new FixedBackOff(properties.retry().backoffMs(), retries));
        handler.setRetryListeners(new AttemptLogger());
        return handler;
    }

    /** Logs ids only: never the payload (CWE-117). */
    private static final class AttemptLogger implements RetryListener {
        @Override
        public void failedDelivery(ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {
            log.warn("Delivery attempt {} failed (topic={}, partition={}, offset={}, error={})",
                    deliveryAttempt, record.topic(), record.partition(), record.offset(),
                    ex.getClass().getSimpleName());
        }

        @Override
        public void recovered(ConsumerRecord<?, ?> record, Exception ex) {
            log.error("Giving up, sent to dead-letter topic (topic={}, partition={}, offset={})",
                    record.topic(), record.partition(), record.offset());
        }
    }
}
