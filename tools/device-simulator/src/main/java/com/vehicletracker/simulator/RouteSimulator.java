package com.vehicletracker.simulator;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Random;

/**
 * Moves a virtual vehicle around a closed loop of waypoints with a plausible speed profile:
 * accelerates and brakes gradually, stops briefly at some waypoints, never teleports.
 * Not thread-safe: one instance per simulated vehicle.
 */
public final class RouteSimulator {

    /** A loop through central London (lat, lon). */
    public static final List<double[]> LONDON_LOOP = List.of(
            new double[]{51.50735, -0.12776},   // Trafalgar Square
            new double[]{51.51130, -0.11980},   // Strand
            new double[]{51.51383, -0.09840},   // St Paul's
            new double[]{51.51334, -0.08890},   // Bank
            new double[]{51.50810, -0.07590},   // Tower Hill
            new double[]{51.50190, -0.08660},   // Borough
            new double[]{51.50330, -0.11360},   // Waterloo
            new double[]{51.50070, -0.12460},   // Westminster
            new double[]{51.50140, -0.14190},   // Buckingham Palace
            new double[]{51.50950, -0.13460});  // Piccadilly

    private static final double EARTH_RADIUS_M = 6_371_000;

    private final List<double[]> waypoints;
    private final Random random;
    private final Clock clock;

    private int segment;            // index of the waypoint we are leaving
    private double segmentProgressM;
    private double speedKph;
    private double targetSpeedKph;
    private int stopTicksLeft;
    private long odometerM;
    private double lastCourse;
    private final boolean ignitionOffAtStops;

    public RouteSimulator(List<double[]> waypoints, long seed, Clock clock) {
        if (waypoints.size() < 2) {
            throw new IllegalArgumentException("Route needs at least two waypoints");
        }
        this.waypoints = waypoints;
        this.random = new Random(seed);
        this.clock = clock;
        this.segment = random.nextInt(waypoints.size());
        this.segmentProgressM = random.nextDouble() * segmentLength(segment);
        this.targetSpeedKph = 20 + random.nextInt(30);
        this.speedKph = targetSpeedKph;
        this.odometerM = 10_000_000L + random.nextInt(50_000_000);
        this.ignitionOffAtStops = random.nextInt(3) == 0;
    }

    /** Advances the vehicle by {@code seconds} and returns the resulting fix. */
    public Fix next(double seconds) {
        if (stopTicksLeft > 0) {
            stopTicksLeft--;
            speedKph = 0;
        } else {
            if (random.nextInt(8) == 0) {
                targetSpeedKph = 15 + random.nextInt(45);
            }
            double maxChange = 12 * seconds / 5;   // ~12 km/h per 5 s
            speedKph += Math.clamp(targetSpeedKph - speedKph, -maxChange, maxChange);
            advance(speedKph / 3.6 * seconds);
        }

        double[] from = waypoints.get(segment);
        double[] to = waypoints.get((segment + 1) % waypoints.size());
        double fraction = segmentProgressM / segmentLength(segment);
        double lat = from[0] + (to[0] - from[0]) * fraction;
        double lon = from[1] + (to[1] - from[1]) * fraction;
        if (speedKph > 0.5) {
            lastCourse = bearing(from, to);
        }
        boolean ignition = !(stopTicksLeft > 0 && ignitionOffAtStops);

        return new Fix(Instant.now(clock), lat, lon, 15 + random.nextInt(10), speedKph, lastCourse,
                7 + random.nextInt(6), true, ignition, odometerM);
    }

    private void advance(double metres) {
        odometerM += Math.round(metres);
        segmentProgressM += metres;
        while (segmentProgressM >= segmentLength(segment)) {
            segmentProgressM -= segmentLength(segment);
            segment = (segment + 1) % waypoints.size();
            if (random.nextInt(3) == 0) {              // stop at roughly a third of the waypoints
                stopTicksLeft = 2 + random.nextInt(4);
                segmentProgressM = 0;
                speedKph = 0;
                break;
            }
        }
    }

    private double segmentLength(int index) {
        return distance(waypoints.get(index), waypoints.get((index + 1) % waypoints.size()));
    }

    static double distance(double[] a, double[] b) {
        double dLat = Math.toRadians(b[0] - a[0]);
        double dLon = Math.toRadians(b[1] - a[1]);
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(a[0])) * Math.cos(Math.toRadians(b[0])) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(h));
    }

    static double bearing(double[] a, double[] b) {
        double lat1 = Math.toRadians(a[0]);
        double lat2 = Math.toRadians(b[0]);
        double dLon = Math.toRadians(b[1] - a[1]);
        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }
}
