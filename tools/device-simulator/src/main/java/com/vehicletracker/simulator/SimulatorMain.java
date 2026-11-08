package com.vehicletracker.simulator;

import com.vehicletracker.simulator.device.DeviceConnection;
import com.vehicletracker.simulator.device.Protocol;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs N simulated trackers, each on its own virtual thread, sending position reports to the
 * ingestion gateway over the real device protocols. Reconnects with back-off on failure.
 */
public final class SimulatorMain {

    private static final Duration MAX_BACKOFF = Duration.ofSeconds(30);

    private final SimulatorConfig config;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong connects = new AtomicLong();

    SimulatorMain(SimulatorConfig config) {
        this.config = config;
    }

    public static void main(String[] args) throws InterruptedException {
        if (List.of(args).contains("--help") || List.of(args).contains("-h")) {
            System.out.println(SimulatorConfig.USAGE);
            return;
        }
        SimulatorConfig config;
        try {
            config = SimulatorConfig.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println(SimulatorConfig.USAGE);
            System.exit(2);
            return;
        }
        new SimulatorMain(config).run();
    }

    void run() throws InterruptedException {
        log("Simulating %d device(s) -> %s, protocols %s, every %s",
                config.devices(), config.host(), config.protocols(), pretty(config.interval()));

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < config.devices(); i++) {
            int index = i;
            threads.add(Thread.ofVirtual().name("device-" + i).start(() -> runDevice(index)));
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> running.set(false)));

        long deadline = config.duration().isZero() ? Long.MAX_VALUE
                : System.nanoTime() + config.duration().toNanos();
        while (running.get() && System.nanoTime() < deadline) {
            Thread.sleep(Math.min(10_000, Math.max(1, (deadline - System.nanoTime()) / 1_000_000)));
            log("stats: sent=%d failures=%d connects=%d", sent.get(), failures.get(), connects.get());
        }
        running.set(false);
        for (Thread t : threads) {
            t.interrupt();
            t.join(2_000);
        }
        log("done: sent=%d failures=%d connects=%d", sent.get(), failures.get(), connects.get());
        if (sent.get() == 0) {
            System.exit(1);
        }
    }

    private void runDevice(int index) {
        Protocol protocol = config.protocolFor(index);
        String imei = config.imeiFor(index);
        int port = config.portFor(protocol);
        RouteSimulator route = new RouteSimulator(RouteSimulator.LONDON_LOOP, imei.hashCode(), Clock.systemUTC());
        double intervalSeconds = config.interval().toMillis() / 1000.0;

        sleepQuietly(Duration.ofMillis(ThreadLocalRandom.current().nextLong(config.interval().toMillis())));

        DeviceConnection connection = null;
        Duration backoff = config.interval();
        while (running.get()) {
            try {
                if (connection == null || !connection.isConnected()) {
                    connection = protocol.connect(config.host(), port, imei);
                    connection.connect();
                    connects.incrementAndGet();
                    log("%s %s connected to %s:%d", protocol, imei, config.host(), port);
                    backoff = config.interval();
                }
                connection.send(route.next(intervalSeconds));
                sent.incrementAndGet();
                sleepQuietly(config.interval());
            } catch (IOException e) {
                failures.incrementAndGet();
                log("%s %s error: %s (retry in %s)", protocol, imei, e.getMessage(), pretty(backoff));
                if (connection != null) {
                    connection.close();
                    connection = null;
                }
                sleepQuietly(backoff);
                backoff = backoff.multipliedBy(2).compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff.multipliedBy(2);
            }
        }
        if (connection != null) {
            connection.close();
        }
    }

    private void sleepQuietly(Duration d) {
        try {
            Thread.sleep(d);
        } catch (InterruptedException e) {
            running.set(false);
            Thread.currentThread().interrupt();
        }
    }

    private static String pretty(Duration d) {
        return d.toMillis() % 1000 == 0 ? d.toSeconds() + "s" : d.toMillis() + "ms";
    }

    private static void log(String format, Object... args) {
        System.out.println(LocalTime.now().truncatedTo(ChronoUnit.SECONDS) + " " + String.format(format, args));
    }
}
