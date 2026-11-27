package com.vehicletracker.livehub.core;

import com.vehicletracker.livehub.config.JacksonJsonCodec;
import com.vehicletracker.model.DeviceEvent;
import com.vehicletracker.model.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientSessionTest {

    static final Instant T0 = Instant.parse("2026-11-27T10:00:00Z");

    /** Clock that tests can move forward. */
    static final class MutableClock extends Clock {
        Instant now = T0;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    static final class FakeSink implements ClientSession.Sink {
        final List<String> sent = new ArrayList<>();
        Integer closeCode;
        @Override public void send(String text) { sent.add(text); }
        @Override public void close(int code, String reason) { closeCode = code; }
        @Override public boolean isOpen() { return closeCode == null; }
    }

    static Position pos(String uid, double lat, double lng, Instant fix, double speed) {
        return new Position(1, uid, "gt06", fix, null, fix, fix, true, lat, lng, null, speed, 90.0, null, 9,
                true, true, null, null, null, null, null, null, null);
    }

    final JsonCodec json = new JacksonJsonCodec();
    final MutableClock clock = new MutableClock();
    final LastPositionStore store = new LastPositionStore();
    final LiveHub hub = new LiveHub(store);
    FakeSink sink;
    ClientSession session;

    @BeforeEach
    void setUp() {
        hub.onPosition(pos("A", 51.50, -0.12, T0, 10));       // central London
        hub.onPosition(pos("B", 51.47, -0.45, T0, 20));       // Heathrow
        hub.onPosition(pos("C", 53.48, -2.24, T0, 30));       // Manchester
        sink = new FakeSink();
        session = new ClientSession("s1", sink, json, store, Authorizer.devToken("secret"), clock, Duration.ofSeconds(10));
        hub.register(session);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> last() {
        return json.read(sink.sent.getLast(), Map.class);
    }

    @SuppressWarnings("unchecked")
    List<String> uids(Map<String, Object> msg, String field) {
        return ((List<Map<String, Object>>) msg.get(field)).stream().map(m -> (String) m.get("deviceUid")).toList();
    }

    void authAndSubscribe(String subscribeJson) {
        session.onText("{\"type\":\"auth\",\"token\":\"secret\"}");
        assertEquals("ready", last().get("type"));
        session.onText(subscribeJson);
    }

    @Test
    void rejectsBadTokenAndClosesConnection() {
        session.onText("{\"type\":\"auth\",\"token\":\"wrong\"}");
        assertEquals("error", last().get("type"));
        assertEquals("unauthorized", last().get("code"));
        assertEquals(ClientSession.CLOSE_POLICY_VIOLATION, sink.closeCode);
        assertFalse(session.isAuthenticated());
    }

    @Test
    void requiresAuthBeforeSubscribe() {
        session.onText("{\"type\":\"subscribe\"}");
        assertEquals("not_authenticated", last().get("code"));
    }

    @Test
    void reportsMalformedAndUnknownMessages() {
        session.onText("not json");
        assertEquals("bad_request", last().get("code"));
        session.onText("{\"type\":\"dance\"}");
        assertEquals("bad_request", last().get("code"));
        assertNull(sink.closeCode, "protocol mistakes do not drop the connection");
    }

    @Test
    void subscribeSendsSnapshotThenCoalescedLiveUpdates() {
        authAndSubscribe("{\"type\":\"subscribe\"}");
        Map<String, Object> snapshot = last();
        assertEquals("snapshot", snapshot.get("type"));
        assertEquals(List.of("A", "B", "C"), uids(snapshot, "positions"));

        hub.onPosition(pos("A", 51.501, -0.121, T0.plusSeconds(5), 11));
        hub.onPosition(pos("B", 51.471, -0.451, T0.plusSeconds(5), 21));
        hub.onPosition(pos("A", 51.502, -0.122, T0.plusSeconds(10), 12));
        int before = sink.sent.size();
        session.flush();
        assertEquals(before + 1, sink.sent.size(), "one batched message");
        Map<String, Object> batch = last();
        assertEquals("positions", batch.get("type"));
        assertEquals(List.of("B", "A"), uids(batch, "positions"), "one entry per device, newest wins");

        session.flush();
        assertEquals(before + 1, sink.sent.size(), "nothing new, nothing sent");
    }

    @Test
    void olderFixesDoNotReachClients() {
        authAndSubscribe("{\"type\":\"subscribe\"}");
        assertFalse(hub.onPosition(pos("A", 51.0, -0.1, T0.minusSeconds(60), 5)), "buffered old fix ignored");
        int before = sink.sent.size();
        session.flush();
        assertEquals(before, sink.sent.size());
        assertEquals(51.50, store.get("A").latitude());
    }

    @Test
    void viewportFiltersSnapshotAndLiveUpdates() {
        authAndSubscribe("{\"type\":\"subscribe\",\"bounds\":{\"n\":51.6,\"s\":51.4,\"e\":0.1,\"w\":-0.3}}");
        assertEquals(List.of("A"), uids(last(), "positions"), "Heathrow is outside the box, Manchester far outside");

        hub.onPosition(pos("C", 53.49, -2.25, T0.plusSeconds(5), 30));
        hub.onPosition(pos("A", 51.51, -0.13, T0.plusSeconds(5), 30));
        session.flush();
        assertEquals(List.of("A"), uids(last(), "positions"));

        session.onText("{\"type\":\"bounds\",\"bounds\":{\"n\":54,\"s\":51,\"e\":0,\"w\":-3}}");
        Map<String, Object> snapshot = last();
        assertEquals("snapshot", snapshot.get("type"));
        assertEquals(List.of("A", "B", "C"), uids(snapshot, "positions"));
    }

    @Test
    void rejectsInvalidBounds() {
        authAndSubscribe("{\"type\":\"subscribe\",\"bounds\":{\"n\":10,\"s\":20,\"e\":0,\"w\":-3}}");
        assertEquals("bad_request", last().get("code"));
    }

    @Test
    void deviceListAndAccessScopeBothApply() {
        session = new ClientSession("s2", sink, json, store,
                token -> AccessScope.devices("alice", Set.of("A", "B"), null), clock, Duration.ofSeconds(10));
        hub.register(session);
        authAndSubscribe("{\"type\":\"subscribe\",\"devices\":[\"B\",\"C\"]}");
        assertEquals(List.of("B"), uids(last(), "positions"), "C is not in the user's scope, A was not requested");

        hub.onEvent(new DeviceEvent(1, "C", "ALARM", T0, T0, null, null, null, null));
        hub.onEvent(new DeviceEvent(1, "B", "IGNITION_ON", T0, T0, null, null, null, null));
        session.flush();
        assertEquals("events", last().get("type"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) last().get("events");
        assertEquals(1, events.size());
        assertEquals("IGNITION_ON", events.getFirst().get("type"));
    }

    @Test
    void closesUnauthenticatedSessionsAfterTimeout() {
        clock.now = T0.plusSeconds(9);
        session.flush();
        assertNull(sink.closeCode);
        clock.now = T0.plusSeconds(10);
        session.flush();
        assertEquals(ClientSession.CLOSE_POLICY_VIOLATION, sink.closeCode);
        assertEquals("not_authenticated", last().get("code"));
    }

    @Test
    void closesSessionWhenTokenExpires() {
        session = new ClientSession("s3", sink, json, store,
                token -> AccessScope.all("bob", T0.plusSeconds(60)), clock, Duration.ofSeconds(10));
        authAndSubscribe("{\"type\":\"subscribe\"}");
        session.flush();
        assertNull(sink.closeCode);
        clock.now = T0.plusSeconds(60);
        session.flush();
        assertEquals(ClientSession.CLOSE_POLICY_VIOLATION, sink.closeCode);
    }

    @Test
    void pingGetsPong() {
        session.onText("{\"type\":\"ping\"}");
        assertEquals("pong", last().get("type"));
    }

    @Test
    void eventBacklogIsCapped() {
        authAndSubscribe("{\"type\":\"subscribe\"}");
        for (int i = 0; i < ClientSession.MAX_PENDING_EVENTS + 25; i++) {
            hub.onEvent(new DeviceEvent(1, "A", "ALARM", T0, T0, null, null, null, (long) i));
        }
        assertEquals(25, session.droppedEvents());
        session.flush();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) last().get("events");
        assertEquals(ClientSession.MAX_PENDING_EVENTS, events.size());
        assertEquals(25, ((Number) events.getFirst().get("gatewayEventId")).intValue(), "oldest dropped first");
    }

    @Test
    void boundsHandleMarginAndAntimeridian() {
        Bounds london = new Bounds(51.6, 51.4, 0.1, -0.3);
        assertTrue(london.contains(51.5, -0.1));
        assertTrue(london.contains(51.61, -0.1), "inside the 10% margin");
        assertFalse(london.contains(51.7, -0.1));
        Bounds pacific = new Bounds(10.0, -10.0, -170.0, 170.0);
        assertTrue(pacific.isValid());
        assertTrue(pacific.contains(0, 179));
        assertTrue(pacific.contains(0, -175));
        assertFalse(pacific.contains(0, 0));
    }

    @Test
    void storeIgnoresDuplicatesAndOlderFixes() {
        Position p = pos("D", 1, 1, T0, 1);
        assertTrue(store.update(p));
        assertFalse(store.update(p), "identical position is not re-sent");
        assertFalse(store.update(pos("D", 2, 2, T0.minusSeconds(1), 1)));
        assertTrue(store.update(pos("D", 3, 3, T0.plusSeconds(1), 1)));
        hub.onDeviceRemoved("D");
        assertNull(store.get("D"));
    }
}
