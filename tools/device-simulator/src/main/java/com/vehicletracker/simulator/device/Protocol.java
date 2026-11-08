package com.vehicletracker.simulator.device;

import java.util.Locale;

/** Protocols the simulator can speak, with Traccar's default listening ports. */
public enum Protocol {

    GT06(5023),
    TELTONIKA(5027),
    H02(5013),
    OSMAND(5055);

    private final int defaultPort;

    Protocol(int defaultPort) {
        this.defaultPort = defaultPort;
    }

    public int defaultPort() {
        return defaultPort;
    }

    public DeviceConnection connect(String host, int port, String imei) {
        return switch (this) {
            case GT06 -> new Gt06Connection(host, port, imei);
            case TELTONIKA -> new TeltonikaConnection(host, port, imei);
            case H02 -> new H02Connection(host, port, imei);
            case OSMAND -> new OsmAndConnection(host, port, imei);
        };
    }

    public static Protocol parse(String name) {
        return valueOf(name.trim().toUpperCase(Locale.ROOT));
    }
}
