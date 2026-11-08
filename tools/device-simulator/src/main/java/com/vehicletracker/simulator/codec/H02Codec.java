package com.vehicletracker.simulator.codec;

import com.vehicletracker.simulator.Fix;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * H02 text protocol (default Traccar port 5013).
 *
 * <pre>
 * *HQ,IMEI,V1,hhmmss,A,ddmm.mmmm,N,dddmm.mmmm,E,speedKnots,course,ddmmyy,STATUS#
 * </pre>
 * STATUS is a 32-bit hex value whose bits are active-low for alarms; bit 10 is ignition.
 */
public final class H02Codec {

    private static final double KPH_PER_KNOT = 1.852;
    private static final long STATUS_NO_ALARMS = 0xFFFFFBFFL;   // all alarm bits idle, ignition (bit 10) off

    private H02Codec() {
    }

    public static byte[] location(String imei, Fix fix) {
        return locationText(imei, fix).getBytes(StandardCharsets.US_ASCII);
    }

    public static String locationText(String imei, Fix fix) {
        ZonedDateTime t = fix.time().atZone(ZoneOffset.UTC);
        long status = fix.ignition() ? STATUS_NO_ALARMS | (1L << 10) : STATUS_NO_ALARMS;
        return String.format(Locale.ROOT, "*HQ,%s,V1,%02d%02d%02d,%s,%s,%s,%s,%s,%.2f,%d,%02d%02d%02d,%08X#",
                imei,
                t.getHour(), t.getMinute(), t.getSecond(),
                fix.valid() ? "A" : "V",
                degreesMinutes(Math.abs(fix.latitude()), 2), fix.latitude() >= 0 ? "N" : "S",
                degreesMinutes(Math.abs(fix.longitude()), 3), fix.longitude() >= 0 ? "E" : "W",
                fix.speedKph() / KPH_PER_KNOT,
                Math.round(fix.course()) % 360,
                t.getDayOfMonth(), t.getMonthValue(), t.getYear() % 100,
                status);
    }

    /** Formats decimal degrees as (d)ddmm.mmmm with the given number of degree digits. */
    static String degreesMinutes(double value, int degreeDigits) {
        int degrees = (int) value;
        double minutes = (value - degrees) * 60;
        if (Math.round(minutes * 10_000) >= 600_000) {   // avoid 60.0000 after rounding
            degrees += 1;
            minutes = 0;
        }
        return String.format(Locale.ROOT, "%0" + degreeDigits + "d%07.4f", degrees, minutes);
    }
}
