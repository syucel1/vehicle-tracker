package com.vehicletracker.livehub.web;

import com.vehicletracker.livehub.config.LiveHubProperties;
import com.vehicletracker.livehub.core.LiveHub;
import com.vehicletracker.livehub.kafka.KafkaFeed;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Small status endpoint for operators and the debug page. */
@RestController
public class StatusController {

    private final LiveHub hub;
    private final KafkaFeed feed;
    private final LiveHubProperties props;

    public StatusController(LiveHub hub, KafkaFeed feed, LiveHubProperties props) {
        this.hub = hub;
        this.feed = feed;
        this.props = props;
    }

    @GetMapping("/api/status")
    public Map<String, Object> status() {
        return Map.of(
                "devices", hub.store().size(),
                "sessions", hub.sessionCount(),
                "snapshotLoaded", feed.isSnapshotLoaded(),
                "authMode", props.getAuth().getMode().name());
    }
}
