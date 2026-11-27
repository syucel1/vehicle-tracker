package com.vehicletracker.livehub.config;

import com.vehicletracker.livehub.core.Authorizer;
import com.vehicletracker.livehub.core.JsonCodec;
import com.vehicletracker.livehub.core.LastPositionStore;
import com.vehicletracker.livehub.core.LiveHub;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LiveHubProperties.class)
public class LiveHubConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LiveHubConfiguration.class);

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    JsonCodec jsonCodec() {
        return new JacksonJsonCodec();
    }

    @Bean
    LiveHub liveHub(MeterRegistry meters) {
        LiveHub hub = new LiveHub(new LastPositionStore());
        Gauge.builder("tracker.livehub.sessions", hub, LiveHub::sessionCount)
                .description("Open WebSocket sessions").register(meters);
        Gauge.builder("tracker.livehub.devices", hub, h -> h.store().size())
                .description("Devices with a known last position").register(meters);
        return hub;
    }

    @Bean
    Authorizer authorizer(LiveHubProperties properties) {
        LiveHubProperties.Auth auth = properties.getAuth();
        if (auth.getMode() == LiveHubProperties.AuthMode.JWT) {
            log.info("WebSocket auth: JWT, keys from {}", auth.getJwkSetUri());
            return new JwtAuthorizer(auth.getJwkSetUri(), auth.getDevicesClaim());
        }
        log.warn("WebSocket auth: DEV shared token. Every client with the token sees every device. "
                + "Set live-hub.auth.mode=JWT outside development.");
        return Authorizer.devToken(auth.getDevToken());
    }
}
