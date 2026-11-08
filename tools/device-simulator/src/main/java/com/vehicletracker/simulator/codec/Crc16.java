package com.vehicletracker.simulator.codec;

/**
 * Table-less CRC-16 implementations used by tracker protocols.
 * Parameters match the definitions in Traccar's {@code Checksum} helper.
 */
public final class Crc16 {

    private Crc16() {
    }

    /** CRC-16/X-25 (poly 0x1021 reflected, init 0xFFFF, xorout 0xFFFF). Used by GT06 / Concox. */
    public static int x25(byte[] data, int offset, int length) {
        int crc = 0xFFFF;
        for (int i = offset; i < offset + length; i++) {
            crc ^= data[i] & 0xFF;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 1) != 0 ? (crc >>> 1) ^ 0x8408 : crc >>> 1;
            }
        }
        return ~crc & 0xFFFF;
    }

    /** CRC-16/ARC a.k.a. CRC-16/IBM (poly 0x8005 reflected, init 0x0000). Used by Teltonika. */
    public static int ibm(byte[] data, int offset, int length) {
        int crc = 0x0000;
        for (int i = offset; i < offset + length; i++) {
            crc ^= data[i] & 0xFF;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 1) != 0 ? (crc >>> 1) ^ 0xA001 : crc >>> 1;
            }
        }
        return crc & 0xFFFF;
    }
}
