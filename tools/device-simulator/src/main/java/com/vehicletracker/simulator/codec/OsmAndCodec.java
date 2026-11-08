package com.vehicletracker.simulator.codec;

import com.vehicletracker.simulator.Fix;

import java.util.Locale;

/**
 * OsmAnd / Traccar Client HTTP protocol (default Traccar port 5055).
 * The device issues {@code GET /?id=...&lat=...&lon=...} ; speed is in knots.
 * Unknown query parameters are stored by Traccar as position attributes.
 */
public final class OsmAndCodec {

    private static final double KPH_PER_KNOT = 1.852;

    private OsmAndCodec() {
    }

    public static String query(String uniqueId, Fix fix) {
        return String.format(Locale.ROOT,
                "/?id=%s&timestamp=%d&lat=%.6f&lon=%.6f&speed=%.2f&bearing=%.1f&altitude=%.1f"
                        + "&accuracy=5&valid=%s&ignition=%s&odometer=%d",
                uniqueId,
                fix.time().getEpochSecond(),
                fix.latitude(),
                fix.longitude(),
                fix.speedKph() / KPH_PER_KNOT,
                fix.course(),
                fix.altitude(),
                fix.valid(),
                fix.ignition(),
                fix.odometerM());
    }
}
