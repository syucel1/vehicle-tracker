package com.vehicletracker.bridge.core;

import com.vehicletracker.bridge.core.TraccarMessages.PositionData;
import com.vehicletracker.bridge.core.TraccarMessages.TraccarPosition;
import com.vehicletracker.model.Position;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Converts a Traccar position into the canonical {@link Position} and applies structural validation.
 * Business-level filtering (dedupe, outlier jumps) belongs to the downstream position processor.
 */
public final class PositionNormalizer {

    static final double KPH_PER_KNOT = 1.852;

    /** Traccar attribute keys that are promoted to first-class fields. */
    private static final Set<String> PROMOTED = Set.of(
            "sat", "ignition", "motion", "odometer", "power", "batteryLevel", "alarm");

    private final Clock clock;
    private final Duration maxFutureSkew;

    public PositionNormalizer(Clock clock, Duration maxFutureSkew) {
        this.clock = clock;
        this.maxFutureSkew = maxFutureSkew;
    }

    public sealed interface Result permits Accepted, Rejected {
    }

    public record Accepted(Position position) implements Result {
    }

    public record Rejected(String deviceUid, String reason, String detail) implements Result {
    }

    public Result normalize(PositionData data) {
        if (data == null || data.position() == null) {
            return new Rejected(null, "missing_position", "Message has no position object");
        }
        String uid = data.device() != null ? trimToNull(data.device().uniqueId()) : null;
        if (uid == null) {
            return new Rejected(null, "missing_device", "Message has no device unique ID");
        }
        TraccarPosition p = data.position();

        Double lat = p.latitude();
        Double lon = p.longitude();
        if (lat == null || lon == null || lat.isNaN() || lon.isNaN()) {
            return new Rejected(uid, "missing_coordinates", "Latitude or longitude missing");
        }
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            return new Rejected(uid, "coordinates_out_of_range", "lat=" + lat + " lon=" + lon);
        }
        if (lat == 0 && lon == 0) {
            return new Rejected(uid, "null_island", "Device reported 0,0 (no position)");
        }

        Instant serverTime;
        Instant deviceTime;
        Instant fixTime;
        try {
            serverTime = Times.parse(p.serverTime());
            deviceTime = Times.parse(p.deviceTime());
            fixTime = Times.parse(p.fixTime());
        } catch (IllegalArgumentException e) {
            return new Rejected(uid, "bad_timestamp", e.getMessage());
        }
        fixTime = firstNonNull(fixTime, deviceTime, serverTime);
        if (fixTime == null) {
            return new Rejected(uid, "missing_timestamp", "No fix, device or server time");
        }
        Instant now = clock.instant();
        if (fixTime.isAfter(now.plus(maxFutureSkew))) {
            return new Rejected(uid, "fix_time_in_future", "fixTime=" + fixTime + " now=" + now);
        }

        Map<String, Object> attrs = p.attributes() != null ? p.attributes() : Map.of();
        Map<String, Object> rest = new LinkedHashMap<>();
        attrs.forEach((k, v) -> {
            if (!PROMOTED.contains(k) && v != null) {
                rest.put(k, v);
            }
        });

        Position position = new Position(
                Position.SCHEMA_VERSION,
                uid,
                p.protocol(),
                fixTime,
                deviceTime != null && !deviceTime.equals(fixTime) ? deviceTime : null,
                serverTime,
                now,
                Boolean.TRUE.equals(p.valid()),
                lat,
                lon,
                p.altitude(),
                p.speed() != null ? round(p.speed() * KPH_PER_KNOT, 2) : null,
                p.course(),
                p.accuracy() != null && p.accuracy() > 0 ? p.accuracy() : null,
                asInteger(attrs.get("sat")),
                asBoolean(attrs.get("ignition")),
                asBoolean(attrs.get("motion")),
                asDouble(attrs.get("odometer")),
                asDouble(attrs.get("power")),
                asDouble(attrs.get("batteryLevel")),
                attrs.get("alarm") != null ? attrs.get("alarm").toString() : null,
                rest.isEmpty() ? null : rest,
                p.deviceId() != null && p.deviceId() != 0 ? p.deviceId() : null,
                p.id() != null && p.id() != 0 ? p.id() : null);
        return new Accepted(position);
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    static Double asDouble(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    static Integer asInteger(Object v) {
        Double d = asDouble(v);
        return d != null ? (int) Math.round(d) : null;
    }

    static Boolean asBoolean(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.intValue() != 0;
        }
        if (v instanceof String s) {
            if ("true".equalsIgnoreCase(s) || "1".equals(s)) {
                return true;
            }
            if ("false".equalsIgnoreCase(s) || "0".equals(s)) {
                return false;
            }
        }
        return null;
    }
}
