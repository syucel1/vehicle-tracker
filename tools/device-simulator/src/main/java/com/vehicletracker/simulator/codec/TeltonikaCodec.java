package com.vehicletracker.simulator.codec;

import com.vehicletracker.simulator.Fix;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Teltonika Codec 8 over TCP (default Traccar port 5027).
 *
 * <pre>
 * Handshake : length(2) | IMEI ASCII          -> server answers 0x01
 * AVL packet: 0x00000000 | dataLength(4) | codec 0x08 | count(1) | records | count(1) | crc(4)
 *             crc = CRC-16/IBM over codec .. second count  -> server answers count(4)
 * Record    : timestamp ms(8) | priority(1) | lon(4) | lat(4) | alt(2) | angle(2) | sats(1) | speed(2)
 *             | eventIoId(1) | totalIo(1) | n1(1) [id,u8]* | n2(1) [id,u16]* | n4(1) [id,u32]* | n8(1) [id,u64]*
 * </pre>
 */
public final class TeltonikaCodec {

    public static final int CODEC_8 = 0x08;

    // Standard FMB IO element ids
    static final int IO_IGNITION = 239;
    static final int IO_MOVEMENT = 240;
    static final int IO_EXTERNAL_VOLTAGE = 66;
    static final int IO_TOTAL_ODOMETER = 16;

    private TeltonikaCodec() {
    }

    public static byte[] handshake(String imei) {
        byte[] ascii = imei.getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeShort(out, ascii.length);
        out.writeBytes(ascii);
        return out.toByteArray();
    }

    public static byte[] avlPacket(List<Fix> fixes) {
        if (fixes.isEmpty() || fixes.size() > 255) {
            throw new IllegalArgumentException("1..255 records per packet");
        }
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(CODEC_8);
        data.write(fixes.size());
        for (Fix fix : fixes) {
            writeRecord(data, fix);
        }
        data.write(fixes.size());
        byte[] payload = data.toByteArray();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeInt(out, 0);                                   // preamble
        writeInt(out, payload.length);
        out.writeBytes(payload);
        writeInt(out, Crc16.ibm(payload, 0, payload.length));
        return out.toByteArray();
    }

    private static void writeRecord(ByteArrayOutputStream out, Fix fix) {
        writeLong(out, fix.time().toEpochMilli());
        out.write(0);                                       // priority: low
        writeInt(out, Math.round(fix.longitude() * 10_000_000));
        writeInt(out, Math.round(fix.latitude() * 10_000_000));
        writeShort(out, (int) Math.round(fix.altitude()));
        writeShort(out, (int) Math.round(fix.course()) % 360);
        out.write(fix.valid() ? fix.satellites() : 0);      // 0 satellites = invalid fix
        writeShort(out, (int) Math.round(fix.speedKph()));

        out.write(0);                                       // event IO id (periodic record)
        out.write(4);                                       // total IO elements
        out.write(2);                                       // 1-byte elements
        out.write(IO_IGNITION);
        out.write(fix.ignition() ? 1 : 0);
        out.write(IO_MOVEMENT);
        out.write(fix.speedKph() > 1 ? 1 : 0);
        out.write(1);                                       // 2-byte elements
        out.write(IO_EXTERNAL_VOLTAGE);
        writeShort(out, fix.ignition() ? 13_800 : 12_600);  // millivolts
        out.write(1);                                       // 4-byte elements
        out.write(IO_TOTAL_ODOMETER);
        writeInt(out, fix.odometerM());
        out.write(0);                                       // 8-byte elements
    }

    private static void writeShort(ByteArrayOutputStream out, int v) {
        out.write((v >> 8) & 0xFF);
        out.write(v & 0xFF);
    }

    private static void writeInt(ByteArrayOutputStream out, long v) {
        out.write((int) (v >> 24) & 0xFF);
        out.write((int) (v >> 16) & 0xFF);
        out.write((int) (v >> 8) & 0xFF);
        out.write((int) v & 0xFF);
    }

    private static void writeLong(ByteArrayOutputStream out, long v) {
        writeInt(out, v >>> 32);
        writeInt(out, v & 0xFFFFFFFFL);
    }
}
