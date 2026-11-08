package com.vehicletracker.simulator;

import com.vehicletracker.simulator.device.DeviceConnection;
import com.vehicletracker.simulator.device.Protocol;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulatorTest {

    @Test
    void generatedImeisAreFifteenDigitsWithValidLuhn() {
        SimulatorConfig config = SimulatorConfig.parse(new String[]{"--devices", "3"});
        assertEquals("359000000000015", config.imeiFor(0));
        for (int i = 0; i < 3; i++) {
            String imei = config.imeiFor(i);
            assertEquals(15, imei.length());
            assertEquals(imei.charAt(14) - '0', SimulatorConfig.luhnCheckDigit(imei.substring(0, 14)));
        }
        // Well-known valid IMEI
        assertEquals(8, SimulatorConfig.luhnCheckDigit("49015420323751"));
    }

    @Test
    void parsesOptions() {
        SimulatorConfig c = SimulatorConfig.parse(new String[]{
                "--host", "gw", "--protocol", "gt06,osmand", "--devices", "5", "--interval", "0.5",
                "--port", "gt06=6000"});
        assertEquals("gw", c.host());
        assertEquals(Protocol.GT06, c.protocolFor(0));
        assertEquals(Protocol.OSMAND, c.protocolFor(1));
        assertEquals(6000, c.portFor(Protocol.GT06));
        assertEquals(5055, c.portFor(Protocol.OSMAND));
        assertEquals(500, c.interval().toMillis());
    }

    @Test
    void routeProducesPlausibleMovement() {
        RouteSimulator route = new RouteSimulator(RouteSimulator.LONDON_LOOP, 42,
                Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC));
        Fix previous = route.next(5);
        for (int i = 0; i < 500; i++) {
            Fix next = route.next(5);
            double moved = RouteSimulator.distance(
                    new double[]{previous.latitude(), previous.longitude()},
                    new double[]{next.latitude(), next.longitude()});
            assertTrue(next.speedKph() >= 0 && next.speedKph() <= 60, "speed " + next.speedKph());
            assertTrue(moved <= 60 / 3.6 * 5 + 1, "no teleporting: moved " + moved + " m");
            assertTrue(next.odometerM() >= previous.odometerM());
            assertTrue(next.latitude() > 51.49 && next.latitude() < 51.52);
            previous = next;
        }
    }

    @Test
    void teltonikaConnectionPerformsHandshakeAndWaitsForAck() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<String> serverSide = CompletableFuture.supplyAsync(() -> {
                try (Socket s = server.accept()) {
                    DataInputStream in = new DataInputStream(s.getInputStream());
                    DataOutputStream out = new DataOutputStream(s.getOutputStream());
                    byte[] imei = new byte[in.readUnsignedShort()];
                    in.readFully(imei);
                    out.write(1);                                   // accept IMEI
                    assertEquals(0, in.readInt());                  // preamble
                    byte[] data = new byte[in.readInt() + 4];
                    in.readFully(data);
                    out.writeInt(data[1]);                          // ack record count
                    return new String(imei);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            DeviceConnection connection = Protocol.TELTONIKA.connect("127.0.0.1", server.getLocalPort(),
                    "359000000000017");
            connection.connect();
            connection.send(new RouteSimulator(RouteSimulator.LONDON_LOOP, 1, Clock.systemUTC()).next(5));
            connection.close();

            assertEquals("359000000000017", serverSide.get(5, TimeUnit.SECONDS));
        }
    }
}
