package com.vehicletracker.simulator.codec;

import com.vehicletracker.simulator.Fix;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TeltonikaCodecTest {

    @Test
    void handshakeIsLengthPrefixedAsciiImei() {
        assertArrayEquals(Hex.decode("000F333532303933303839313638363631"),
                TeltonikaCodec.handshake("352093089168661"));
    }

    @Test
    void avlPacketHasValidFramingAndRecordFields() {
        Instant time = Instant.parse("2026-11-06T18:27:04.250Z");
        Fix fix = new Fix(time, -33.8688, 151.2093, 58, 64.6, 271, 11, true, false, 123_456_789);
        byte[] packet = TeltonikaCodec.avlPacket(List.of(fix));
        ByteBuffer b = ByteBuffer.wrap(packet);

        assertEquals(0, b.getInt(), "preamble");
        int dataLength = b.getInt();
        assertEquals(packet.length - 12, dataLength);
        assertEquals(0x08, b.get());
        assertEquals(1, b.get());

        assertEquals(time.toEpochMilli(), b.getLong());
        assertEquals(0, b.get());
        assertEquals(151.2093, b.getInt() / 1e7, 1e-7);
        assertEquals(-33.8688, b.getInt() / 1e7, 1e-7);
        assertEquals(58, b.getShort());
        assertEquals(271, b.getShort());
        assertEquals(11, b.get());
        assertEquals(65, b.getShort());

        assertEquals(0, b.get());                // event io id
        assertEquals(4, b.get());                // total io
        assertEquals(2, b.get());                // n1
        assertEquals(239, b.get() & 0xFF);
        assertEquals(0, b.get());                // ignition off
        assertEquals(240, b.get() & 0xFF);
        assertEquals(1, b.get());                // moving
        assertEquals(1, b.get());                // n2
        assertEquals(66, b.get());
        assertEquals(12_600, b.getShort() & 0xFFFF);
        assertEquals(1, b.get());                // n4
        assertEquals(16, b.get());
        assertEquals(123_456_789, b.getInt());
        assertEquals(0, b.get());                // n8

        assertEquals(1, b.get(), "trailing record count");
        assertEquals(Crc16.ibm(packet, 8, dataLength), b.getInt());
        assertFalse(b.hasRemaining());
    }
}
