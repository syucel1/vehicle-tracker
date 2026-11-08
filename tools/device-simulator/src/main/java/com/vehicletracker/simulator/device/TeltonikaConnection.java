package com.vehicletracker.simulator.device;

import com.vehicletracker.simulator.Fix;
import com.vehicletracker.simulator.codec.TeltonikaCodec;

import java.io.IOException;
import java.util.List;

final class TeltonikaConnection extends TcpConnection {

    private final String imei;

    TeltonikaConnection(String host, int port, String imei) {
        super(host, port);
        this.imei = imei;
    }

    @Override
    public void connect() throws IOException {
        openSocket();
        write(TeltonikaCodec.handshake(imei));
        int accepted = in.readUnsignedByte();
        if (accepted != 0x01) {
            close();
            throw new IOException("Server rejected IMEI " + imei);
        }
    }

    @Override
    public void send(Fix fix) throws IOException {
        write(TeltonikaCodec.avlPacket(List.of(fix)));
        int acknowledged = in.readInt();   // server confirms the number of records it accepted
        if (acknowledged != 1) {
            throw new IOException("Server acknowledged " + acknowledged + " records, expected 1");
        }
    }
}
