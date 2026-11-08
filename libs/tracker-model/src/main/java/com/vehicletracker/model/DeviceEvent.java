package com.vehicletracker.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Canonical device event. Published to {@link Topics#DEVICE_EVENTS} keyed by {@link #deviceUid()}.
 *
 * @param schemaVersion  message schema version (currently 1)
 * @param deviceUid      device unique ID (usually the IMEI)
 * @param type           upper snake case, e.g. DEVICE_ONLINE, DEVICE_OFFLINE, IGNITION_ON, ALARM
 * @param eventTime      when the event happened (UTC)
 * @param receivedAt     when this service published the event
 * @param latitude       position at the time of the event, if known
 * @param longitude      position at the time of the event, if known
 * @param attributes     event details, e.g. {"alarm": "sos"}
 * @param gatewayEventId internal event id in the ingestion gateway (for tracing)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeviceEvent(
        int schemaVersion,
        String deviceUid,
        String type,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant eventTime,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant receivedAt,
        Double latitude,
        Double longitude,
        Map<String, Object> attributes,
        Long gatewayEventId) {

    public static final int SCHEMA_VERSION = 1;
}
