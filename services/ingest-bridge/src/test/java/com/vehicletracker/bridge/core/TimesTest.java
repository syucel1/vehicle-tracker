package com.vehicletracker.bridge.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimesTest {

    private static final Instant T = Instant.parse("2026-11-06T18:27:04Z");

    @Test
    void parsesFormatsTraccarCanEmit() {
        assertEquals(T, Times.parse("2026-11-06T18:27:04.000+00:00"));
        assertEquals(T, Times.parse("2026-11-06T18:27:04.000+0000"));
        assertEquals(T, Times.parse("2026-11-06T19:27:04.000+01:00"));
        assertEquals(T, Times.parse("2026-11-06T18:27:04Z"));
        assertEquals(T, Times.parse(String.valueOf(T.toEpochMilli())));
    }

    @Test
    void blankIsNullAndGarbageFails() {
        assertNull(Times.parse(null));
        assertNull(Times.parse(" "));
        assertThrows(IllegalArgumentException.class, () -> Times.parse("06/11/2026"));
    }
}
