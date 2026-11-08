package com.vehicletracker.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * A device that is sending data but is not registered. Published to {@link Topics#DEVICE_UNKNOWN}
 * (at most once per device per report interval) so operators can onboard or block it.
 *
 * @param deviceUid       unique ID reported on the wire
 * @param protocol        wire protocol it used
 * @param seenAt          time of the report that triggered this notice
 * @param latitude        last reported latitude
 * @param longitude       last reported longitude
 * @param gatewayDeviceId internal device id in the ingestion gateway
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record UnknownDevice(
        String deviceUid,
        String protocol,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant seenAt,
        Double latitude,
        Double longitude,
        Long gatewayDeviceId) {
}
