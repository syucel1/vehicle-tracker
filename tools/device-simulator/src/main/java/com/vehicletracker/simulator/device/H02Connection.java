package com.vehicletracker.simulator.device;

import com.vehicletracker.simulator.Fix;
import com.vehicletracker.simulator.codec.H02Codec;

import java.io.IOException;

final class H02Connection extends TcpConnection {

    private final String imei;

    H02Connection(String host, int port, String imei) {
        super(host, port);
        this.imei = imei;
    }

    @Override
    public void connect() throws IOException {
        openSocket();     // H02 has no login; the device is identified by every message
        startDraining();
    }

    @Override
    public void send(Fix fix) throws IOException {
        write(H02Codec.location(imei, fix));
    }
}
