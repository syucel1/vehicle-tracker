package com.vehicletracker.simulator.codec;

import com.vehicletracker.simulator.Fix;

import java.io.ByteArrayOutputStream;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * GT06 / Concox binary protocol (default Traccar port 5023).
 *
 * <pre>
 * 0x78 0x78 | length(1) | type(1) | content(n) | serial(2) | crc16-x25(2) | 0x0D 0x0A
 * length = type + content + serial + crc ;  crc covers length .. serial
 * </pre>
 */
public final class Gt06Codec {

    public static final int MSG_LOGIN = 0x01;
    public static final int MSG_GPS_LBS = 0x12;
    public static final int MSG_HEARTBEAT = 0x13;

    // Cell tower info for the LBS block (UK MCC 234). Values are illustrative.
    private static final int MCC = 234;
    private static final int MNC = 10;
    private static final int LAC = 0x1234;
    private static final int CELL_ID = 0x001F71;

    private Gt06Codec() {
    }

    /** Login packet: terminal ID is the 15-digit IMEI as 8 BCD bytes with a leading zero nibble. */
    public static byte[] login(String imei, int serial) {
        if (imei.length() != 15 || !imei.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("GT06 requires a 15-digit IMEI: " + imei);
        }
        return frame(MSG_LOGIN, Hex.decode("0" + imei), serial);
    }

    /** GPS + LBS location packet (type 0x12). */
    public static byte[] location(Fix fix, int serial) {
        ByteArrayOutputStream content = new ByteArrayOutputStream();

        ZonedDateTime t = fix.time().atZone(ZoneOffset.UTC);
        content.write(t.getYear() % 100);
        content.write(t.getMonthValue());
        content.write(t.getDayOfMonth());
        content.write(t.getHour());
        content.write(t.getMinute());
        content.write(t.getSecond());

        // GPS info length (upper nibble, always 12) + satellites (lower nibble)
        content.write(0xC0 | Math.min(fix.satellites(), 15));

        writeInt(content, Math.round(Math.abs(fix.latitude()) * 60 * 30000));
        writeInt(content, Math.round(Math.abs(fix.longitude()) * 60 * 30000));
        content.write((int) Math.min(255, Math.round(fix.speedKph())));

        int flags = ((int) Math.round(fix.course()) % 360) & 0x3FF;
        if (fix.latitude() >= 0) {
            flags |= 1 << 10;               // north
        }
        if (fix.longitude() < 0) {
            flags |= 1 << 11;               // west
        }
        if (fix.valid()) {
            flags |= 1 << 12;               // GPS positioned
        }
        flags |= 1 << 14;                   // ACC state present
        if (fix.ignition()) {
            flags |= 1 << 15;               // ACC on
        }
        writeShort(content, flags);

        writeShort(content, MCC);
        content.write(MNC);
        writeShort(content, LAC);
        content.write((CELL_ID >> 16) & 0xFF);
        writeShort(content, CELL_ID & 0xFFFF);

        return frame(MSG_GPS_LBS, content.toByteArray(), serial);
    }

    static byte[] frame(int type, byte[] content, int serial) {
        int length = 1 + content.length + 2 + 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x78);
        out.write(0x78);
        out.write(length);
        out.write(type);
        out.writeBytes(content);
        writeShort(out, serial & 0xFFFF);
        byte[] partial = out.toByteArray();
        writeShort(out, Crc16.x25(partial, 2, partial.length - 2));
        out.write(0x0D);
        out.write(0x0A);
        return out.toByteArray();
    }

    private static void writeShort(ByteArrayOutputStream out, int value) {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeInt(ByteArrayOutputStream out, long value) {
        out.write((int) (value >> 24) & 0xFF);
        out.write((int) (value >> 16) & 0xFF);
        out.write((int) (value >> 8) & 0xFF);
        out.write((int) value & 0xFF);
    }
}
