package com.swayam.rewards.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.swayam.rewards.model.MemberPoints;
import com.swayam.rewards.repository.MemberPointsRepository;
import com.swayam.rewards.repository.ProcessedEventRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(OutputCaptureExtension.class)
class PurchasePipelineIntegrationTest extends AbstractIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(30);

    @Autowired
    private TestRestTemplate http;
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private MemberPointsRepository memberPoints;
    @Autowired
    private ProcessedEventRepository processedEvents;
    @Value("${rewards.topics.purchases}")
    private String purchasesTopic;
    @Value("${rewards.topics.purchases-dlt}")
    private String dltTopic;

    @Test
    void purchase_normalEvent_creditsPointsOnce() {
        String member = uniqueId("member");
        String event = uniqueId("event");

        ResponseEntity<String> response = post(event, member, 99);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        await().atMost(WAIT).untilAsserted(() -> assertThat(balanceOf(member)).contains(9L));
        assertThat(processedEvents.existsById(event)).isTrue();
    }

    @Test
    void purchase_sameEventIdTwice_creditsPointsOnce() {
        String member = uniqueId("member");
        String duplicated = uniqueId("event");
        String marker = uniqueId("marker");

        post(duplicated, member, 100);
        post(duplicated, member, 100);
        // Events of one member share a partition, so once the marker is processed both copies were handled.
        post(marker, member, 10);

        await().atMost(WAIT).untilAsserted(() -> assertThat(processedEvents.existsById(marker)).isTrue());
        assertThat(balanceOf(member)).contains(11L);   // 10 (once) + 1 (marker), not 21
        assertThat(processedEvents.existsById(duplicated)).isTrue();
        assertThat(processedEvents.findAll().stream().filter(e -> e.getMemberId().equals(member)).count())
                .isEqualTo(2);
    }

    @Test
    void purchase_invalidEvent_isRetriedThreeTimesThenLandsOnDeadLetterTopic(CapturedOutput output) {
        String member = uniqueId("member");
        // Valid JSON but a negative amount: breaks the validation rules inside the consumer.
        String invalid = "{\"eventId\":\"" + uniqueId("event") + "\",\"memberId\":\"" + member
                + "\",\"amountRupees\":-5}";

        kafkaTemplate.send(purchasesTopic, member, invalid);

        ConsumerRecord<String, String> dead = awaitDeadLetter(member);
        assertThat(dead.value()).isEqualTo(invalid);
        assertThat(balanceOf(member)).isEmpty();
        assertThat(countOccurrences(output.getAll(), "Delivery attempt 1 failed")).isGreaterThanOrEqualTo(1);
        assertThat(output.getAll()).contains("Delivery attempt 2 failed", "Delivery attempt 3 failed");
        assertThat(output.getAll()).doesNotContain("Delivery attempt 4 failed");
    }

    @Test
    void purchase_unreadableMessage_landsOnDeadLetterTopic() {
        String member = uniqueId("member");

        kafkaTemplate.send(purchasesTopic, member, "this is not json");

        assertThat(awaitDeadLetter(member).value()).isEqualTo("this is not json");
    }

    private ResponseEntity<String> post(String eventId, String memberId, long amount) {
        Map<String, Object> body = Map.of("eventId", eventId, "memberId", memberId, "amountRupees", amount);
        return http.postForEntity("/purchases", body, String.class);
    }

    private Optional<Long> balanceOf(String memberId) {
        return memberPoints.findById(memberId).map(MemberPoints::getBalance);
    }

    private ConsumerRecord<String, String> awaitDeadLetter(String key) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, uniqueId("dlt-reader"),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> reader = new KafkaConsumer<>(config)) {
            reader.subscribe(List.of(dltTopic));
            ConsumerRecord<String, String>[] found = new ConsumerRecord[1];
            await().atMost(WAIT).until(() -> {
                for (ConsumerRecord<String, String> record : reader.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        found[0] = record;
                    }
                }
                return found[0] != null;
            });
            return found[0];
        }
    }

    private static int countOccurrences(String text, String part) {
        return text.split(part, -1).length - 1;
    }

    private static String uniqueId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
