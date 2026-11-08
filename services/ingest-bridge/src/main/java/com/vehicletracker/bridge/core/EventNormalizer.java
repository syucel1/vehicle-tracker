package com.vehicletracker.bridge.core;

import com.vehicletracker.bridge.core.TraccarMessages.EventData;
import com.vehicletracker.bridge.core.TraccarMessages.TraccarPosition;
import com.vehicletracker.model.DeviceEvent;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Converts a Traccar event (deviceOnline, ignitionOn, alarm, ...) into a canonical {@link DeviceEvent}. */
public final class EventNormalizer {

    private final Clock clock;

    public EventNormalizer(Clock clock) {
        this.clock = clock;
    }

    /** Returns null when the message is not a usable event. */
    public DeviceEvent normalize(EventData data) {
        if (data == null || data.event() == null || data.device() == null) {
            return null;
        }
        String uid = PositionNormalizer.trimToNull(data.device().uniqueId());
        String type = PositionNormalizer.trimToNull(data.event().type());
        if (uid == null || type == null) {
            return null;
        }
        Instant now = clock.instant();
        Instant eventTime;
        try {
            eventTime = Times.parse(data.event().eventTime());
        } catch (IllegalArgumentException e) {
            eventTime = null;
        }

        Double lat = null;
        Double lon = null;
        TraccarPosition p = data.position();
        if (p != null && p.latitude() != null && p.longitude() != null
                && !(p.latitude() == 0 && p.longitude() == 0)) {
            lat = p.latitude();
            lon = p.longitude();
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        if (data.event().attributes() != null) {
            data.event().attributes().forEach((k, v) -> {
                if (v != null) {
                    attributes.put(k, v);
                }
            });
        }
        if (data.event().geofenceId() != null && data.event().geofenceId() != 0) {
            attributes.put("gatewayGeofenceId", data.event().geofenceId());
        }

        return new DeviceEvent(
                DeviceEvent.SCHEMA_VERSION,
                uid,
                toUpperSnake(type),
                eventTime != null ? eventTime : now,
                now,
                lat,
                lon,
                attributes.isEmpty() ? null : attributes,
                data.event().id() != null && data.event().id() != 0 ? data.event().id() : null);
    }

    /** deviceOnline -> DEVICE_ONLINE */
    static String toUpperSnake(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
    }
}
