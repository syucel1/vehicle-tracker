package com.vehicletracker.bridge.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Shapes of the JSON produced by Traccar's Kafka forwarders ({@code PositionData}, {@code EventData}).
 * Only the fields we use are declared; everything else is ignored so gateway upgrades that add
 * fields do not break the bridge. Boxed types are used so absent values stay null.
 * Dates are kept as strings and parsed leniently by {@link Times}.
 */
public final class TraccarMessages {

    private TraccarMessages() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PositionData(TraccarPosition position, TraccarDevice device) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EventData(TraccarEvent event, TraccarPosition position, TraccarDevice device) {
    }

    /** Traccar position. Note: {@code speed} is in knots. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TraccarPosition(
            Long id,
            Long deviceId,
            String protocol,
            String serverTime,
            String deviceTime,
            String fixTime,
            Boolean valid,
            Double latitude,
            Double longitude,
            Double altitude,
            Double speed,
            Double course,
            Double accuracy,
            Map<String, Object> attributes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TraccarDevice(
            Long id,
            String name,
            String uniqueId,
            String status,
            String model,
            Boolean disabled) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TraccarEvent(
            Long id,
            String type,
            String eventTime,
            Long deviceId,
            Long positionId,
            Long geofenceId,
            Map<String, Object> attributes) {
    }
}
