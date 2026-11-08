package com.vehicletracker.simulator;

import java.time.Instant;

/**
 * One GPS report as a tracker would produce it.
 *
 * @param time       GNSS fix time (UTC)
 * @param latitude   decimal degrees, negative = south
 * @param longitude  decimal degrees, negative = west
 * @param altitude   metres above sea level
 * @param speedKph   ground speed in km/h
 * @param course     heading in degrees, 0..359
 * @param satellites satellites in use
 * @param valid      true when the receiver has a fix
 * @param ignition   ignition (ACC) line state
 * @param odometerM  cumulative distance in metres
 */
public record Fix(
        Instant time,
        double latitude,
        double longitude,
        double altitude,
        double speedKph,
        double course,
        int satellites,
        boolean valid,
        boolean ignition,
        long odometerM) {
}
