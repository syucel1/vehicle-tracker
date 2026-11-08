package com.vehicletracker.simulator.codec;

import com.vehicletracker.simulator.Fix;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Gt06CodecTest {

    @Test
    void loginMatchesTraccarTestVector() {
        // From Traccar's Gt06ProtocolDecoderTest: IMEI 864717003283581, serial 0x0009
        assertArrayEquals(Hex.decode("78780D01086471700328358100093F040D0A"),
                Gt06Codec.login("864717003283581", 9));
    }

    @Test
    void loginRejectsMalformedImei() {
        assertThrows(IllegalArgumentException.class, () -> Gt06Codec.login("12345", 1));
    }

    @Test
    void locationPacketDecodesBackToTheFix() {
        Fix fix = new Fix(Instant.parse("2026-11-06T18:27:04Z"), 51.50735, -0.12776, 20, 42, 118, 9,
                true, true, 0);
        byte[] packet = Gt06Codec.location(fix, 0x0102);
        ByteBuffer b = ByteBuffer.wrap(packet);

        assertEquals(0x7878, b.getShort() & 0xFFFF);
        int length = b.get() & 0xFF;
        assertEquals(packet.length - 5, length, "length covers type..crc");
        assertEquals(0x12, b.get());
        assertArrayEquals(new byte[]{26, 11, 6, 18, 27, 4}, new byte[]{b.get(), b.get(), b.get(), b.get(), b.get(), b.get()});
        assertEquals(0xC9, b.get() & 0xFF);                       // gps info length 12, 9 satellites
        assertEquals(51.50735, (b.getInt() & 0xFFFFFFFFL) / 60.0 / 30000.0, 1e-5);
        assertEquals(0.12776, (b.getInt() & 0xFFFFFFFFL) / 60.0 / 30000.0, 1e-5);
        assertEquals(42, b.get() & 0xFF);
        int flags = b.getShort() & 0xFFFF;
        assertEquals(118, flags & 0x3FF);
        assertTrue((flags & (1 << 10)) != 0, "north");
        assertTrue((flags & (1 << 11)) != 0, "west");
        assertTrue((flags & (1 << 12)) != 0, "valid");
        assertTrue((flags & (1 << 15)) != 0, "ignition on");
        b.position(b.position() + 8);                             // LBS block
        assertEquals(0x0102, b.getShort() & 0xFFFF);

        int crc = b.getShort() & 0xFFFF;
        assertEquals(Crc16.x25(packet, 2, packet.length - 6), crc);
        assertEquals(0x0D0A, b.getShort() & 0xFFFF);
        assertFalse(b.hasRemaining());
    }
}
