package com.vehicletracker.bridge;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** End-to-end through a real (embedded) Kafka broker: gateway JSON in, canonical record out. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "tracker.devices.mode=OPEN"
        })
@EmbeddedKafka(partitions = 1, topics = {
        "traccar.positions", "traccar.events", "positions.raw", "positions.rejected", "device.events",
        "device.unknown"})
class IngestBridgeIntegrationTest {

    @Autowired
    KafkaTemplate<String, String> template;

    @Autowired
    EmbeddedKafkaBroker broker;

    @Test
    void gatewayPositionIsRepublishedAsCanonicalPosition() throws Exception {
        template.send("traccar.positions", "7", fixture("position-gt06.json")).get(10, TimeUnit.SECONDS);
        ConsumerRecord<String, String> record = awaitRecord("positions.raw", "359000000000015");
        assertTrue(record.value().contains("\"protocol\":\"gt06\""), record.value());
        assertTrue(record.value().contains("\"deviceUid\":\"359000000000015\""), record.value());
    }

    @Test
    void garbageIsRoutedToRejectedTopic() throws Exception {
        template.send("traccar.positions", "bad-key", "not json at all").get(10, TimeUnit.SECONDS);
        ConsumerRecord<String, String> record = awaitRecord("positions.rejected", "bad-key");
        assertTrue(record.value().contains("\"reason\":\"unparseable\""), record.value());
    }

    @Test
    void gatewayEventIsRepublishedAsCanonicalEvent() throws Exception {
        template.send("traccar.events", "7", fixture("event-ignition-on.json")).get(10, TimeUnit.SECONDS);
        ConsumerRecord<String, String> record = awaitRecord("device.events", "359000000000015");
        assertTrue(record.value().contains("\"type\":\"IGNITION_ON\""), record.value());
    }

    private ConsumerRecord<String, String> awaitRecord(String topic, String key) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        return record;
                    }
                }
            }
        }
        return fail("No record with key " + key + " on " + topic);
    }

    private static String fixture(String name) throws Exception {
        try (InputStream in = IngestBridgeIntegrationTest.class.getResourceAsStream("/traccar/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
