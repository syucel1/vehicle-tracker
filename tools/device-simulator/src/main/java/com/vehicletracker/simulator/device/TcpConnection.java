package com.vehicletracker.simulator.device;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;

/** Shared TCP plumbing: connect, write, read exact bytes, or drain unsolicited server responses. */
abstract class TcpConnection implements DeviceConnection {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    protected static final int READ_TIMEOUT_MS = 10_000;

    protected final String host;
    protected final int port;
    protected final AtomicLong bytesReceived = new AtomicLong();

    private Socket socket;
    protected OutputStream out;
    protected DataInputStream in;
    private Thread drainThread;

    TcpConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    protected void openSocket() throws IOException {
        socket = new Socket();
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        socket.setSoTimeout(READ_TIMEOUT_MS);
        out = socket.getOutputStream();
        in = new DataInputStream(socket.getInputStream());
    }

    protected void write(byte[] bytes) throws IOException {
        out.write(bytes);
        out.flush();
    }

    /**
     * For protocols where the server's replies are informational, read them on a background
     * virtual thread so the socket's receive buffer never fills up.
     */
    protected void startDraining() {
        InputStream stream = in;
        drainThread = Thread.ofVirtual().name("drain-" + port).start(() -> {
            byte[] buffer = new byte[512];
            try {
                socket.setSoTimeout(0);
                int n;
                while ((n = stream.read(buffer)) >= 0) {
                    bytesReceived.addAndGet(n);
                }
            } catch (IOException ignored) {
                // socket closed
            }
        });
    }

    @Override
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void close() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // best effort
        }
        if (drainThread != null) {
            drainThread.interrupt();
        }
    }
}
