package com.vehicletracker.simulator.codec;

import com.vehicletracker.simulator.Fix;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextCodecsTest {

    private final Fix fix = new Fix(Instant.parse("2026-11-06T08:05:09Z"), 51.50735, -0.12776, 21, 37.04, 118,
            9, true, true, 4_200_000);

    @Test
    void h02MessageHasExpectedLayout() {
        String text = H02Codec.locationText("359000000000017", fix);
        assertEquals("*HQ,359000000000017,V1,080509,A,5130.4410,N,00007.6656,W,20.00,118,061126,FFFFFFFF#", text);
    }

    @Test
    void h02MatchesTraccarDecoderPatternShape() {
        // Simplified form of the pattern used by Traccar's H02ProtocolDecoder for V1 messages
        Pattern v1 = Pattern.compile("\\*HQ,\\d+,V1,\\d{6},[AV],\\d{4}\\.\\d{4},[NS],\\d{5}\\.\\d{4},[EW],"
                + "\\d+\\.\\d+,\\d+,\\d{6},[0-9A-F]{8}#");
        assertTrue(v1.matcher(H02Codec.locationText("1", fix)).matches());
    }

    @Test
    void h02DegreesMinutesNeverPrintsSixtyMinutes() {
        assertEquals("5200.0000", H02Codec.degreesMinutes(51.9999999, 2));
        assertEquals("00007.6656", H02Codec.degreesMinutes(0.12776, 3));
    }

    @Test
    void osmAndQueryCarriesAllFields() {
        String q = OsmAndCodec.query("abc", fix);
        assertEquals("/?id=abc&timestamp=1793952309&lat=51.507350&lon=-0.127760&speed=20.00&bearing=118.0"
                + "&altitude=21.0&accuracy=5&valid=true&ignition=true&odometer=4200000", q);
    }
}
