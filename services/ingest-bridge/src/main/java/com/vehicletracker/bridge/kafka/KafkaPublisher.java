package com.vehicletracker.bridge.kafka;

import com.vehicletracker.bridge.core.BridgeProcessor.Outbound;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Sends a batch of outbound records and blocks until the broker has acknowledged all of them
 * (producer uses acks=all + idempotence). Throwing here makes the listener container retry the
 * batch without committing offsets, so nothing is lost if Kafka is briefly unavailable.
 */
@Component
public class KafkaPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 30;

    private final KafkaTemplate<String, String> template;

    public KafkaPublisher(KafkaTemplate<String, String> template) {
        this.template = template;
    }

    public void publishAll(List<Outbound> records) {
        if (records.isEmpty()) {
            return;
        }
        CompletableFuture<?>[] futures = records.stream()
                .map(r -> template.send(r.topic(), r.key(), r.value()))
                .toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(futures).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed to publish " + records.size() + " record(s) to Kafka", e);
        }
    }
}
