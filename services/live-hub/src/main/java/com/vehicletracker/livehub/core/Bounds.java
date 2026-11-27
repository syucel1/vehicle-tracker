package com.vehicletracker.livehub.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A map viewport sent by the client. Positions are delivered when they fall inside the box
 * enlarged by {@link #MARGIN} on every side, so markers just off-screen keep moving.
 * Viewports crossing the antimeridian (west > east) are supported.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Bounds(Double n, Double s, Double e, Double w) {

    static final double MARGIN = 0.10;

    public boolean isValid() {
        return n != null && s != null && e != null && w != null
                && n >= s && n <= 90 && s >= -90 && e >= -180 && e <= 180 && w >= -180 && w <= 180;
    }

    public boolean contains(double lat, double lng) {
        double latPad = (n - s) * MARGIN;
        if (lat > n + latPad || lat < s - latPad) {
            return false;
        }
        if (w <= e) {
            double lngPad = (e - w) * MARGIN;
            return lng >= w - lngPad && lng <= e + lngPad;
        }
        // crosses the antimeridian
        return lng >= w || lng <= e;
    }
}
