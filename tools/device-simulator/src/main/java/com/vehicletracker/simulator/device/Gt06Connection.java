package com.vehicletracker.simulator.device;

import com.vehicletracker.simulator.Fix;
import com.vehicletracker.simulator.codec.Gt06Codec;

import java.io.IOException;

final class Gt06Connection extends TcpConnection {

    private final String imei;
    private int serial = 1;

    Gt06Connection(String host, int port, String imei) {
        super(host, port);
        this.imei = imei;
    }

    @Override
    public void connect() throws IOException {
        openSocket();
        write(Gt06Codec.login(imei, nextSerial()));
        // Login ACK: 78 78 05 01 <serial:2> <crc:2> 0D 0A
        byte[] ack = new byte[10];
        in.readFully(ack);
        if ((ack[0] & 0xFF) != 0x78 || (ack[1] & 0xFF) != 0x78 || ack[3] != Gt06Codec.MSG_LOGIN) {
            close();
            throw new IOException("Unexpected GT06 login response");
        }
        startDraining();
    }

    @Override
    public void send(Fix fix) throws IOException {
        write(Gt06Codec.location(fix, nextSerial()));
    }

    private int nextSerial() {
        int s = serial;
        serial = serial == 0xFFFF ? 1 : serial + 1;
        return s;
    }
}
