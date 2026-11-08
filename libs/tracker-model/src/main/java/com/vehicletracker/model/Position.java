package com.vehicletracker.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Canonical position report, independent of the device's wire protocol.
 * Published to {@link Topics#POSITIONS_RAW} keyed by {@link #deviceUid()}.
 * Units are SI-ish and explicit in the field names; optional values are null when the device did not report them.
 *
 * @param schemaVersion     message schema version (currently 1)
 * @param deviceUid         device unique ID as reported on the wire (usually the IMEI)
 * @param protocol          wire protocol name, e.g. "gt06", "teltonika"
 * @param fixTime           time of the GNSS fix (UTC)
 * @param deviceTime        device clock time when the report was created, if different from fixTime
 * @param serverTime        time the gateway received the report
 * @param receivedAt        time this service published the canonical message
 * @param valid             true when the receiver reported a valid fix
 * @param latitude          WGS84 decimal degrees
 * @param longitude         WGS84 decimal degrees
 * @param altitudeM         metres above sea level
 * @param speedKph          ground speed in km/h
 * @param course            heading in degrees (0 = north)
 * @param accuracyM         horizontal accuracy in metres
 * @param satellites        satellites in use
 * @param ignition          ignition / ACC state
 * @param motion            device-reported motion state
 * @param odometerM         device odometer in metres
 * @param powerV            external power voltage
 * @param batteryLevel      internal battery level in percent
 * @param alarm             alarm(s) raised by the device, comma separated (e.g. "sos,overspeed")
 * @param attributes        all other protocol-specific values, passed through untouched
 * @param gatewayDeviceId   internal device id in the ingestion gateway (for tracing)
 * @param gatewayPositionId internal position id in the ingestion gateway (for tracing)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record Position(
        int schemaVersion,
        String deviceUid,
        String protocol,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant fixTime,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant deviceTime,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant serverTime,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant receivedAt,
        boolean valid,
        double latitude,
        double longitude,
        Double altitudeM,
        Double speedKph,
        Double course,
        Double accuracyM,
        Integer satellites,
        Boolean ignition,
        Boolean motion,
        Double odometerM,
        Double powerV,
        Double batteryLevel,
        String alarm,
        Map<String, Object> attributes,
        Long gatewayDeviceId,
        Long gatewayPositionId) {

    public static final int SCHEMA_VERSION = 1;
}
