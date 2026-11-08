package com.vehicletracker.model;

/**
 * Kafka topic names. Every topic is keyed by the device's unique ID (IMEI) unless noted,
 * so all messages from one device land on one partition and stay in order.
 */
public final class Topics {

    /** Raw output of the Traccar gateway's position forwarder (keyed by Traccar's internal device id). */
    public static final String TRACCAR_POSITIONS = "traccar.positions";

    /** Raw output of the Traccar gateway's event forwarder (keyed by Traccar's internal device id). */
    public static final String TRACCAR_EVENTS = "traccar.events";

    /** Canonical, validated positions from registered devices. Value: {@link Position}. */
    public static final String POSITIONS_RAW = "positions.raw";

    /** Messages that could not be parsed or failed validation. Value: {@link RejectedMessage}. */
    public static final String POSITIONS_REJECTED = "positions.rejected";

    /** Canonical device events (online/offline, ignition, alarms...). Value: {@link DeviceEvent}. */
    public static final String DEVICE_EVENTS = "device.events";

    /** Devices that report but are not registered. Value: {@link UnknownDevice}. */
    public static final String DEVICE_UNKNOWN = "device.unknown";

    private Topics() {
    }
}
