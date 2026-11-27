package com.vehicletracker.livehub.config;

import com.vehicletracker.model.Topics;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Settings under the {@code live-hub.*} prefix. */
@ConfigurationProperties(prefix = "live-hub")
public class LiveHubProperties {

    /** Kafka bootstrap servers. */
    private String bootstrapServers = "localhost:29092";
    /** Compacted topic with the latest position per device (snapshot + live updates). */
    private String positionsTopic = Topics.POSITIONS_LATEST;
    /** Device events topic (read from the end: only new events are pushed). */
    private String eventsTopic = Topics.DEVICE_EVENTS;
    /** Origins allowed to open the WebSocket, e.g. https://console.example.com. "*" for development. */
    private List<String> allowedOrigins = new ArrayList<>(List.of("*"));
    /** Close connections that have not authenticated within this time. */
    private Duration authTimeout = Duration.ofSeconds(10);
    private final Auth auth = new Auth();

    public enum AuthMode { DEV, JWT }

    public static class Auth {
        /** DEV: one shared token sees every device. JWT: validate tokens from the identity provider. */
        private AuthMode mode = AuthMode.DEV;
        /** Shared token for DEV mode. */
        private String devToken = "dev-token";
        /** JWKS endpoint of the identity provider, e.g. http://keycloak:8080/realms/vt/protocol/openid-connect/certs */
        private String jwkSetUri;
        /** JWT claim listing the device IDs the user may see; the value "*" means all devices. */
        private String devicesClaim = "vt_devices";

        public AuthMode getMode() { return mode; }
        public void setMode(AuthMode mode) { this.mode = mode; }
        public String getDevToken() { return devToken; }
        public void setDevToken(String devToken) { this.devToken = devToken; }
        public String getJwkSetUri() { return jwkSetUri; }
        public void setJwkSetUri(String jwkSetUri) { this.jwkSetUri = jwkSetUri; }
        public String getDevicesClaim() { return devicesClaim; }
        public void setDevicesClaim(String devicesClaim) { this.devicesClaim = devicesClaim; }
    }

    public String getBootstrapServers() { return bootstrapServers; }
    public void setBootstrapServers(String bootstrapServers) { this.bootstrapServers = bootstrapServers; }
    public String getPositionsTopic() { return positionsTopic; }
    public void setPositionsTopic(String positionsTopic) { this.positionsTopic = positionsTopic; }
    public String getEventsTopic() { return eventsTopic; }
    public void setEventsTopic(String eventsTopic) { this.eventsTopic = eventsTopic; }
    public List<String> getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    public Duration getAuthTimeout() { return authTimeout; }
    public void setAuthTimeout(Duration authTimeout) { this.authTimeout = authTimeout; }
    public Auth getAuth() { return auth; }
}
