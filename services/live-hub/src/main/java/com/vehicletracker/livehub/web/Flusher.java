package com.vehicletracker.livehub.web;

import com.vehicletracker.livehub.core.LiveHub;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Pushes the coalesced updates of every session at a fixed rate (default every 250 ms). */
@Component
public class Flusher {

    private final LiveHub hub;

    public Flusher(LiveHub hub) {
        this.hub = hub;
    }

    @Scheduled(fixedDelayString = "${live-hub.flush-interval-ms:250}")
    public void flush() {
        hub.flushAll();
    }
}
