package com.vehicletracker.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * A message that could not be turned into a canonical record. Published to
 * {@link Topics#POSITIONS_REJECTED} so decoding or data-quality problems are visible, not silently dropped.
 *
 * @param sourceTopic topic the original message came from
 * @param key         original message key
 * @param deviceUid   device unique ID if it could be determined
 * @param reason      short machine-friendly reason, e.g. "coordinates_out_of_range"
 * @param detail      human-readable detail
 * @param payload     the original message value
 * @param rejectedAt  when it was rejected
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record RejectedMessage(
        String sourceTopic,
        String key,
        String deviceUid,
        String reason,
        String detail,
        String payload,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant rejectedAt) {
}
