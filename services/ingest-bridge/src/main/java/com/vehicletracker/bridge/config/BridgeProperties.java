package com.vehicletracker.bridge.config;

import com.vehicletracker.bridge.core.DeviceRegistry;
import com.vehicletracker.model.Topics;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Settings under the {@code tracker.*} prefix. */
@ConfigurationProperties(prefix = "tracker")
public class BridgeProperties {

    private final Devices devices = new Devices();
    private final Validation validation = new Validation();
    private final TopicNames topics = new TopicNames();

    public Devices getDevices() {
        return devices;
    }

    public Validation getValidation() {
        return validation;
    }

    public TopicNames getTopics() {
        return topics;
    }

    public static class Devices {
        /** OPEN accepts every device; ALLOW_LIST accepts only {@link #allowed}. */
        private DeviceRegistry.Mode mode = DeviceRegistry.Mode.OPEN;
        /** Unique IDs (IMEIs) accepted in ALLOW_LIST mode. */
        private List<String> allowed = new ArrayList<>();
        /** Minimum time between two device.unknown notices for the same device. */
        private Duration unknownReportInterval = Duration.ofMinutes(10);

        public DeviceRegistry.Mode getMode() {
            return mode;
        }

        public void setMode(DeviceRegistry.Mode mode) {
            this.mode = mode;
        }

        public List<String> getAllowed() {
            return allowed;
        }

        public void setAllowed(List<String> allowed) {
            this.allowed = allowed;
        }

        public Duration getUnknownReportInterval() {
            return unknownReportInterval;
        }

        public void setUnknownReportInterval(Duration unknownReportInterval) {
            this.unknownReportInterval = unknownReportInterval;
        }
    }

    public static class Validation {
        /** Reject fixes timestamped further than this into the future (device clock errors). */
        private Duration maxFutureSkew = Duration.ofMinutes(5);

        public Duration getMaxFutureSkew() {
            return maxFutureSkew;
        }

        public void setMaxFutureSkew(Duration maxFutureSkew) {
            this.maxFutureSkew = maxFutureSkew;
        }
    }

    public static class TopicNames {
        private String gatewayPositions = Topics.TRACCAR_POSITIONS;
        private String gatewayEvents = Topics.TRACCAR_EVENTS;
        private String positionsRaw = Topics.POSITIONS_RAW;
        private String positionsRejected = Topics.POSITIONS_REJECTED;
        private String deviceEvents = Topics.DEVICE_EVENTS;
        private String deviceUnknown = Topics.DEVICE_UNKNOWN;

        public String getGatewayPositions() {
            return gatewayPositions;
        }

        public void setGatewayPositions(String gatewayPositions) {
            this.gatewayPositions = gatewayPositions;
        }

        public String getGatewayEvents() {
            return gatewayEvents;
        }

        public void setGatewayEvents(String gatewayEvents) {
            this.gatewayEvents = gatewayEvents;
        }

        public String getPositionsRaw() {
            return positionsRaw;
        }

        public void setPositionsRaw(String positionsRaw) {
            this.positionsRaw = positionsRaw;
        }

        public String getPositionsRejected() {
            return positionsRejected;
        }

        public void setPositionsRejected(String positionsRejected) {
            this.positionsRejected = positionsRejected;
        }

        public String getDeviceEvents() {
            return deviceEvents;
        }

        public void setDeviceEvents(String deviceEvents) {
            this.deviceEvents = deviceEvents;
        }

        public String getDeviceUnknown() {
            return deviceUnknown;
        }

        public void setDeviceUnknown(String deviceUnknown) {
            this.deviceUnknown = deviceUnknown;
        }
    }
}
