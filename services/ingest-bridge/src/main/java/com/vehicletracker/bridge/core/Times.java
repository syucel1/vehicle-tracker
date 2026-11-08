package com.vehicletracker.bridge.core;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/** Lenient parser for the date formats Traccar's ObjectMapper can emit. */
public final class Times {

    // "+0000" style offsets (no colon) as written by some Jackson versions
    private static final Pattern OFFSET_WITHOUT_COLON = Pattern.compile("([+-]\\d{2})(\\d{2})$");

    private Times() {
    }

    /** Returns null for null/blank input; throws {@link IllegalArgumentException} for unparseable values. */
    public static Instant parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        if (v.chars().allMatch(Character::isDigit)) {
            return Instant.ofEpochMilli(Long.parseLong(v));
        }
        try {
            return OffsetDateTime.parse(v).toInstant();       // 2026-11-06T18:27:04.000+00:00
        } catch (DateTimeParseException ignored) {
            // try other forms
        }
        try {
            return Instant.parse(v);                         // 2026-11-06T18:27:04Z
        } catch (DateTimeParseException ignored) {
            // try other forms
        }
        try {
            return OffsetDateTime.parse(OFFSET_WITHOUT_COLON.matcher(v).replaceFirst("$1:$2")).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Unparseable time: " + value, e);
        }
    }
}
