package com.vehicletracker.bridge.core;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** Limits device.unknown notices to one per device per interval, so a chatty unregistered tracker can't flood the topic. */
public final class UnknownDeviceThrottle {

    private static final int MAX_TRACKED = 100_000;

    private final Clock clock;
    private final Duration interval;
    private final ConcurrentHashMap<String, Instant> lastReported = new ConcurrentHashMap<>();

    public UnknownDeviceThrottle(Clock clock, Duration interval) {
        this.clock = clock;
        this.interval = interval;
    }

    public boolean shouldReport(String deviceUid) {
        if (lastReported.size() > MAX_TRACKED) {
            lastReported.clear();
        }
        Instant now = clock.instant();
        boolean[] report = {false};
        lastReported.compute(deviceUid, (k, previous) -> {
            if (previous == null || !now.isBefore(previous.plus(interval))) {
                report[0] = true;
                return now;
            }
            return previous;
        });
        return report[0];
    }
}
