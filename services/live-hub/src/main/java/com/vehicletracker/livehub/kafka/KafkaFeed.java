package com.vehicletracker.livehub.kafka;

import com.vehicletracker.livehub.config.LiveHubProperties;
import com.vehicletracker.livehub.core.JsonCodec;
import com.vehicletracker.livehub.core.LiveHub;
import com.vehicletracker.model.DeviceEvent;
import com.vehicletracker.model.Position;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Feeds the {@link LiveHub} from Kafka. Every Live Hub instance reads every partition itself
 * (manual assignment, no consumer group), because every instance must see every vehicle to serve
 * its own WebSocket clients.
 * <ul>
 *   <li>{@code positions.latest} (compacted) is read from the beginning: this rebuilds the
 *       last-known-position snapshot on start-up, then continues with live updates.</li>
 *   <li>{@code device.events} is read from the end: only new events are pushed.</li>
 * </ul>
 */
@Component
public class KafkaFeed implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(KafkaFeed.class);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(5);

    private final LiveHub hub;
    private final JsonCodec json;
    private final LiveHubProperties props;
    private final Counter positions;
    private final Counter events;
    private final Counter unreadable;

    private volatile boolean running;
    private volatile boolean snapshotLoaded;
    private volatile KafkaConsumer<String, String> consumer;
    private Thread thread;

    public KafkaFeed(LiveHub hub, JsonCodec json, LiveHubProperties props, MeterRegistry meters) {
        this.hub = hub;
        this.json = json;
        this.props = props;
        this.positions = Counter.builder("tracker.livehub.kafka.records").tag("kind", "position").register(meters);
        this.events = Counter.builder("tracker.livehub.kafka.records").tag("kind", "event").register(meters);
        this.unreadable = Counter.builder("tracker.livehub.kafka.records").tag("kind", "unreadable").register(meters);
    }

    public boolean isSnapshotLoaded() {
        return snapshotLoaded;
    }

    @Override
    public void start() {
        running = true;
        thread = Thread.ofPlatform().name("kafka-feed").daemon(true).start(this::loop);
    }

    @Override
    public void stop() {
        running = false;
        KafkaConsumer<String, String> c = consumer;
        if (c != null) {
            c.wakeup();
        }
        if (thread != null) {
            try {
                thread.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void loop() {
        while (running) {
            try {
                consume();
            } catch (WakeupException e) {
                if (!running) {
                    return;
                }
            } catch (RuntimeException e) {
                snapshotLoaded = false;
                log.warn("Kafka feed failed ({}), retrying in {}s", e.getMessage(), RETRY_DELAY.toSeconds());
                try {
                    Thread.sleep(RETRY_DELAY);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void consume() {
        try (KafkaConsumer<String, String> c = new KafkaConsumer<>(consumerProperties())) {
            consumer = c;
            List<TopicPartition> positionPartitions = partitions(c, props.getPositionsTopic());
            List<TopicPartition> eventPartitions = partitions(c, props.getEventsTopic());
            List<TopicPartition> all = new ArrayList<>(positionPartitions);
            all.addAll(eventPartitions);
            c.assign(all);
            c.seekToBeginning(positionPartitions);
            c.seekToEnd(eventPartitions);
            Map<TopicPartition, Long> snapshotEnd = c.endOffsets(positionPartitions);
            log.info("Loading last positions from {} ({} partitions)", props.getPositionsTopic(), positionPartitions.size());

            while (running) {
                for (ConsumerRecord<String, String> r : c.poll(Duration.ofMillis(500))) {
                    handle(r);
                }
                if (!snapshotLoaded && positionPartitions.stream().allMatch(tp -> c.position(tp) >= snapshotEnd.get(tp))) {
                    snapshotLoaded = true;
                    log.info("Snapshot loaded: {} devices", hub.store().size());
                }
            }
        } finally {
            consumer = null;
        }
    }

    private void handle(ConsumerRecord<String, String> r) {
        try {
            if (r.topic().equals(props.getPositionsTopic())) {
                if (r.value() == null) {
                    if (r.key() != null) {
                        hub.onDeviceRemoved(r.key());
                    }
                    return;
                }
                hub.onPosition(json.read(r.value(), Position.class));
                positions.increment();
            } else {
                hub.onEvent(json.read(r.value(), DeviceEvent.class));
                events.increment();
            }
        } catch (RuntimeException e) {
            unreadable.increment();
            log.debug("Skipping unreadable record {}-{}@{}: {}", r.topic(), r.partition(), r.offset(), e.getMessage());
        }
    }

    private static List<TopicPartition> partitions(KafkaConsumer<String, String> c, String topic) {
        List<PartitionInfo> infos = c.partitionsFor(topic, Duration.ofSeconds(30));
        if (infos == null || infos.isEmpty()) {
            throw new IllegalStateException("Topic " + topic + " does not exist yet");
        }
        return infos.stream().map(i -> new TopicPartition(i.topic(), i.partition())).toList();
    }

    private Properties consumerProperties() {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, props.getBootstrapServers());
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        p.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "2000");
        p.put(ConsumerConfig.CLIENT_ID_CONFIG, "live-hub-" + hostname());
        return p;
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
