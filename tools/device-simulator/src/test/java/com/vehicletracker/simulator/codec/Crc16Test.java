package com.vehicletracker.simulator.codec;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Crc16Test {

    private static final byte[] CHECK = "123456789".getBytes(StandardCharsets.US_ASCII);

    @Test
    void x25MatchesStandardCheckValue() {
        assertEquals(0x906E, Crc16.x25(CHECK, 0, CHECK.length));
    }

    @Test
    void ibmMatchesStandardCheckValue() {
        assertEquals(0xBB3D, Crc16.ibm(CHECK, 0, CHECK.length));
    }

    @Test
    void ibmMatchesTraccarTeltonikaVector() {
        // Codec 8 packet from Traccar's TeltonikaProtocolDecoderTest; CRC trailer is 0x0000EE8D
        byte[] packet = Hex.decode("000000000000004c08010000016818d500580009c28d9f1cb3757a00be00c60f0053000f06f001"
                + "1503c80001011d00fc0007423799180053cdf80dce426f430f88190bb8560bb802f100005aa110002887e000010000ee8d");
        int dataLength = 0x4c;
        assertEquals(0xEE8D, Crc16.ibm(packet, 8, dataLength));
    }
}
