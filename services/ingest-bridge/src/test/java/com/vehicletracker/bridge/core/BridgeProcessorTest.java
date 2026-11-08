package com.vehicletracker.bridge.core;

import com.vehicletracker.bridge.core.BridgeProcessor.Outcome;
import com.vehicletracker.bridge.core.BridgeProcessor.Outbound;
import com.vehicletracker.bridge.kafka.JacksonPayloadCodec;
import com.vehicletracker.model.DeviceEvent;
import com.vehicletracker.model.Position;
import com.vehicletracker.model.RejectedMessage;
import com.vehicletracker.model.UnknownDevice;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeProcessorTest {

    private static final Instant NOW = Instant.parse("2026-11-06T18:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final BridgeProcessor.Topics TOPICS =
            new BridgeProcessor.Topics("positions.raw", "positions.rejected", "device.events", "device.unknown");

    private final PayloadCodec codec = new JacksonPayloadCodec();

    private BridgeProcessor processor(DeviceRegistry registry) {
        return new BridgeProcessor(codec, new PositionNormalizer(CLOCK, Duration.ofMinutes(5)),
                new EventNormalizer(CLOCK), registry, new UnknownDeviceThrottle(CLOCK, Duration.ofMinutes(10)),
                TOPICS, CLOCK);
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = BridgeProcessorTest.class.getResourceAsStream("/traccar/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void realTraccarPositionBecomesCanonicalPositionKeyedByImei() throws IOException {
        BridgeProcessor.Result result = processor(DeviceRegistry.of(DeviceRegistry.Mode.OPEN, List.of()))
                .onGatewayPosition("traccar.positions", "7", fixture("position-gt06.json"));

        assertEquals(Outcome.ACCEPTED, result.outcome());
        Outbound out = result.records().getFirst();
        assertEquals("positions.raw", out.topic());
        assertEquals("359000000000015", out.key());

        Position p = codec.read(out.value(), Position.class);
        assertEquals("gt06", p.protocol());
        assertEquals(51.50735, p.latitude());
        assertEquals(-0.12776, p.longitude());
        assertEquals(42.0, p.speedKph(), 0.01);
        assertEquals(Instant.parse("2026-11-06T18:27:04Z"), p.fixTime());
        assertEquals(9, p.satellites());
        assertEquals(true, p.ignition());
        assertEquals(4_200_000.0, p.odometerM());
        assertEquals(4, ((Number) p.attributes().get("rssi")).intValue());
        assertEquals(7L, p.gatewayDeviceId());

        assertTrue(out.value().contains("\"fixTime\":\"2026-11-06T18:27:04Z\""), "ISO-8601 timestamps: " + out.value());
        assertTrue(!out.value().contains("\"deviceTime\""), "nulls are omitted");
    }

    @Test
    void unregisteredDeviceIsReportedOnceThenSuppressed() throws IOException {
        BridgeProcessor processor = processor(DeviceRegistry.of(DeviceRegistry.Mode.ALLOW_LIST, Set.of("111")));
        String payload = fixture("position-gt06.json");

        BridgeProcessor.Result first = processor.onGatewayPosition("traccar.positions", "7", payload);
        assertEquals(Outcome.UNKNOWN_DEVICE, first.outcome());
        assertEquals("device.unknown", first.records().getFirst().topic());
        UnknownDevice notice = codec.read(first.records().getFirst().value(), UnknownDevice.class);
        assertEquals("359000000000015", notice.deviceUid());
        assertEquals("gt06", notice.protocol());

        BridgeProcessor.Result second = processor.onGatewayPosition("traccar.positions", "7", payload);
        assertEquals(Outcome.UNKNOWN_DEVICE_SUPPRESSED, second.outcome());
        assertTrue(second.records().isEmpty());
    }

    @Test
    void garbageGoesToRejectedTopicWithOriginalPayload() {
        BridgeProcessor.Result result = processor(DeviceRegistry.of(DeviceRegistry.Mode.OPEN, List.of()))
                .onGatewayPosition("traccar.positions", "9", "{not json");
        assertEquals(Outcome.REJECTED, result.outcome());
        Outbound out = result.records().getFirst();
        assertEquals("positions.rejected", out.topic());
        RejectedMessage rejected = codec.read(out.value(), RejectedMessage.class);
        assertEquals("unparseable", rejected.reason());
        assertEquals("{not json", rejected.payload());
        assertEquals("traccar.positions", rejected.sourceTopic());
    }

    @Test
    void invalidCoordinatesAreRejectedWithDeviceUid() throws IOException {
        String payload = fixture("position-gt06.json").replace("\"latitude\":51.50735", "\"latitude\":0.0")
                .replace("\"longitude\":-0.12776", "\"longitude\":0.0");
        BridgeProcessor.Result result = processor(DeviceRegistry.of(DeviceRegistry.Mode.OPEN, List.of()))
                .onGatewayPosition("traccar.positions", "7", payload);
        RejectedMessage rejected = codec.read(result.records().getFirst().value(), RejectedMessage.class);
        assertEquals("null_island", rejected.reason());
        assertEquals("359000000000015", rejected.deviceUid());
        assertEquals("359000000000015", result.records().getFirst().key());
    }

    @Test
    void traccarEventBecomesCanonicalEvent() throws IOException {
        BridgeProcessor.Result result = processor(DeviceRegistry.of(DeviceRegistry.Mode.OPEN, List.of()))
                .onGatewayEvent("traccar.events", "7", fixture("event-ignition-on.json"));
        assertEquals(Outcome.ACCEPTED, result.outcome());
        Outbound out = result.records().getFirst();
        assertEquals("device.events", out.topic());
        assertEquals("359000000000015", out.key());
        DeviceEvent event = codec.read(out.value(), DeviceEvent.class);
        assertEquals("IGNITION_ON", event.type());
        assertEquals(Instant.parse("2026-11-06T18:27:04Z"), event.eventTime());
        assertEquals(51.5133, event.latitude());
        assertEquals(31L, event.gatewayEventId());
    }

    @Test
    void eventTypeNaming() {
        assertEquals("DEVICE_ONLINE", EventNormalizer.toUpperSnake("deviceOnline"));
        assertEquals("ALARM", EventNormalizer.toUpperSnake("alarm"));
        assertEquals("GEOFENCE_EXIT", EventNormalizer.toUpperSnake("geofenceExit"));
    }
}
