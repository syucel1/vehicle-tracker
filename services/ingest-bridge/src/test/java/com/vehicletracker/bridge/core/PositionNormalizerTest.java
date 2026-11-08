package com.vehicletracker.bridge.core;

import com.vehicletracker.bridge.core.PositionNormalizer.Accepted;
import com.vehicletracker.bridge.core.PositionNormalizer.Rejected;
import com.vehicletracker.bridge.core.TraccarMessages.PositionData;
import com.vehicletracker.bridge.core.TraccarMessages.TraccarDevice;
import com.vehicletracker.bridge.core.TraccarMessages.TraccarPosition;
import com.vehicletracker.model.Position;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionNormalizerTest {

    private static final Instant NOW = Instant.parse("2026-11-06T18:30:00Z");
    private final PositionNormalizer normalizer =
            new PositionNormalizer(Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5));

    private static TraccarPosition position(Double lat, Double lon, String fixTime, Map<String, Object> attrs) {
        return new TraccarPosition(42L, 7L, "teltonika", "2026-11-06T18:27:05.000+00:00",
                "2026-11-06T18:27:04.000+00:00", fixTime, true, lat, lon, 21.0, 10.0, 270.0, 0.0, attrs);
    }

    private static PositionData data(TraccarPosition p) {
        return new PositionData(p, new TraccarDevice(7L, "Van", " 359000000000015 ", "online", null, false));
    }

    @Test
    void mapsFieldsAndConvertsUnits() {
        Map<String, Object> attrs = Map.of("sat", 11, "ignition", true, "motion", false, "odometer", 123456.0,
                "power", 13.8, "batteryLevel", 87, "alarm", "sos", "rpm", 2100);
        Position p = ((Accepted) normalizer.normalize(
                data(position(51.5, -0.12, "2026-11-06T18:27:04.000+00:00", attrs)))).position();

        assertEquals(Position.SCHEMA_VERSION, p.schemaVersion());
        assertEquals("359000000000015", p.deviceUid(), "unique id is trimmed");
        assertEquals("teltonika", p.protocol());
        assertEquals(Instant.parse("2026-11-06T18:27:04Z"), p.fixTime());
        assertNull(p.deviceTime(), "device time omitted when equal to fix time");
        assertEquals(Instant.parse("2026-11-06T18:27:05Z"), p.serverTime());
        assertEquals(NOW, p.receivedAt());
        assertTrue(p.valid());
        assertEquals(18.52, p.speedKph(), 1e-9, "10 knots = 18.52 km/h");
        assertEquals(270.0, p.course());
        assertNull(p.accuracyM(), "zero accuracy means unknown");
        assertEquals(11, p.satellites());
        assertEquals(true, p.ignition());
        assertEquals(false, p.motion());
        assertEquals(123456.0, p.odometerM());
        assertEquals(13.8, p.powerV());
        assertEquals(87.0, p.batteryLevel());
        assertEquals("sos", p.alarm());
        assertEquals(Map.of("rpm", 2100), p.attributes(), "promoted keys are not duplicated");
        assertEquals(7L, p.gatewayDeviceId());
        assertEquals(42L, p.gatewayPositionId());
    }

    @Test
    void rejectsStructurallyBadPositions() {
        assertReason("missing_position", new PositionData(null, null));
        assertReason("missing_device", new PositionData(position(51.0, 0.1, null, null), null));
        assertReason("missing_coordinates", data(position(null, 0.1, null, null)));
        assertReason("coordinates_out_of_range", data(position(91.0, 0.1, null, null)));
        assertReason("coordinates_out_of_range", data(position(10.0, -180.5, null, null)));
        assertReason("null_island", data(position(0.0, 0.0, null, null)));
        assertReason("bad_timestamp", data(position(51.0, 0.1, "yesterday", null)));
        assertReason("fix_time_in_future", data(position(51.0, 0.1, "2026-11-06T18:40:00Z", null)));
    }

    @Test
    void acceptsSmallClockSkewAndFallsBackToDeviceTime() {
        assertInstanceOf(Accepted.class, normalizer.normalize(data(position(51.0, 0.1, "2026-11-06T18:33:00Z", null))));
        Position p = ((Accepted) normalizer.normalize(data(position(51.0, 0.1, null, null)))).position();
        assertEquals(Instant.parse("2026-11-06T18:27:04Z"), p.fixTime());
        assertNull(p.attributes());
    }

    @Test
    void attributeCoercion() {
        assertEquals(true, PositionNormalizer.asBoolean("true"));
        assertEquals(false, PositionNormalizer.asBoolean(0));
        assertNull(PositionNormalizer.asBoolean("maybe"));
        assertEquals(12.5, PositionNormalizer.asDouble("12.5"));
        assertNull(PositionNormalizer.asDouble("n/a"));
        assertEquals(8, PositionNormalizer.asInteger(7.6));
    }

    private void assertReason(String reason, PositionData data) {
        PositionNormalizer.Result result = normalizer.normalize(data);
        Rejected rejected = assertInstanceOf(Rejected.class, result, "expected rejection " + reason);
        assertEquals(reason, rejected.reason());
        assertFalse(rejected.detail().isBlank());
    }
}
