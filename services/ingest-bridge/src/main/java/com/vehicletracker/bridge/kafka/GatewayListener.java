package com.vehicletracker.bridge.kafka;

import com.vehicletracker.bridge.core.BridgeProcessor;
import com.vehicletracker.bridge.core.BridgeProcessor.Outbound;
import com.vehicletracker.bridge.core.BridgeProcessor.Outcome;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Consumes the gateway's raw Kafka output in batches, converts each message with {@link BridgeProcessor}
 * and publishes the canonical records. Offsets are committed by the container only after
 * {@link #handle} returns, i.e. after everything was acknowledged by Kafka.
 */
@Component
public class GatewayListener {

    private static final Logger log = LoggerFactory.getLogger(GatewayListener.class);

    private final BridgeProcessor processor;
    private final KafkaPublisher publisher;
    private final MeterRegistry meters;

    public GatewayListener(BridgeProcessor processor, KafkaPublisher publisher, MeterRegistry meters) {
        this.processor = processor;
        this.publisher = publisher;
        this.meters = meters;
    }

    @KafkaListener(topics = "${tracker.topics.gateway-positions:traccar.positions}", batch = "true")
    public void onPositions(List<ConsumerRecord<String, String>> records) {
        handle("position", records, processor::onGatewayPosition);
    }

    @KafkaListener(topics = "${tracker.topics.gateway-events:traccar.events}", batch = "true")
    public void onEvents(List<ConsumerRecord<String, String>> records) {
        handle("event", records, processor::onGatewayEvent);
    }

    private void handle(String kind, List<ConsumerRecord<String, String>> records,
                        TriFunction<String, String, String, BridgeProcessor.Result> process) {
        List<Outbound> outbound = new ArrayList<>(records.size());
        Map<Outcome, Integer> outcomes = new EnumMap<>(Outcome.class);
        for (ConsumerRecord<String, String> record : records) {
            BridgeProcessor.Result result = process.apply(record.topic(), record.key(), record.value());
            outbound.addAll(result.records());
            outcomes.merge(result.outcome(), 1, Integer::sum);
            if (result.outcome() == Outcome.REJECTED && log.isDebugEnabled()) {
                log.debug("Rejected {} from {}: {}", kind, record.topic(), result.records());
            }
        }

        publisher.publishAll(outbound);

        outcomes.forEach((outcome, count) -> Counter.builder("tracker.bridge.messages")
                .description("Gateway messages processed by the ingest bridge")
                .tag("kind", kind)
                .tag("outcome", outcome.name().toLowerCase(Locale.ROOT))
                .register(meters)
                .increment(count));
        log.debug("Processed {} {} message(s): {}", records.size(), kind, outcomes);
    }

    @FunctionalInterface
    interface TriFunction<A, B, C, R> {
        R apply(A a, B b, C c);
    }
}
