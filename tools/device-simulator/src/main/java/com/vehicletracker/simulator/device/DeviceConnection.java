package com.vehicletracker.simulator.device;

import com.vehicletracker.simulator.Fix;

import java.io.Closeable;
import java.io.IOException;

/** A live link between one simulated tracker and the backend, speaking one wire protocol. */
public interface DeviceConnection extends Closeable {

    /** Opens the transport and performs any protocol login/handshake. */
    void connect() throws IOException;

    /** Sends one position report (and waits for the protocol ACK where the protocol has one). */
    void send(Fix fix) throws IOException;

    boolean isConnected();

    @Override
    void close();
}
