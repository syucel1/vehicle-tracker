package com.vehicletracker.livehub.core;

import com.vehicletracker.livehub.core.Messages.ClientMessage;
import com.vehicletracker.livehub.core.Messages.ServerMessage;
import com.vehicletracker.model.DeviceEvent;
import com.vehicletracker.model.Position;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Protocol state of one WebSocket client, independent of the WebSocket library.
 * <p>
 * Live positions are coalesced per device between flushes: a slow client receives the newest
 * position of each vehicle rather than a growing backlog, which is all a live map needs.
 * Thread-safe: Kafka, the flush timer and the socket call into it concurrently.
 */
public final class ClientSession {

    /** Outbound side of the connection, implemented by the WebSocket adapter. */
    public interface Sink {
        void send(String text);

        void close(int code, String reason);

        boolean isOpen();
    }

    public static final int CLOSE_POLICY_VIOLATION = 1008;
    static final int MAX_PENDING_EVENTS = 500;

    private final String id;
    private final Sink sink;
    private final JsonCodec json;
    private final LastPositionStore store;
    private final Authorizer authorizer;
    private final Clock clock;
    private final Instant openedAt;
    private final Duration authTimeout;

    private AccessScope scope;
    private boolean subscribed;
    private Set<String> devices;          // null = every device in scope
    private Bounds bounds;                // null = no viewport filter
    private final Map<String, Position> pending = new LinkedHashMap<>();
    private final ArrayDeque<DeviceEvent> pendingEvents = new ArrayDeque<>();
    private long droppedEvents;

    public ClientSession(String id, Sink sink, JsonCodec json, LastPositionStore store, Authorizer authorizer,
                         Clock clock, Duration authTimeout) {
        this.id = id;
        this.sink = sink;
        this.json = json;
        this.store = store;
        this.authorizer = authorizer;
        this.clock = clock;
        this.authTimeout = authTimeout;
        this.openedAt = clock.instant();
    }

    public String id() {
        return id;
    }

    public synchronized boolean isAuthenticated() {
        return scope != null;
    }

    public synchronized long droppedEvents() {
        return droppedEvents;
    }

    // ---------------------------------------------------------------- inbound

    public synchronized void onText(String text) {
        ClientMessage msg;
        try {
            msg = json.read(text, ClientMessage.class);
        } catch (RuntimeException e) {
            send(ServerMessage.error("bad_request", "Message is not valid JSON"));
            return;
        }
        String type = msg.type() == null ? "" : msg.type();
        switch (type) {
            case "auth" -> onAuth(msg);
            case "subscribe" -> onSubscribe(msg);
            case "bounds" -> onBounds(msg);
            case "ping" -> send(ServerMessage.pong(clock.instant()));
            default -> send(ServerMessage.error("bad_request", "Unknown message type: " + type));
        }
    }

    private void onAuth(ClientMessage msg) {
        if (scope != null) {
            send(ServerMessage.error("bad_request", "Already authenticated"));
            return;
        }
        try {
            scope = authorizer.authorize(msg.token());
        } catch (Authorizer.AuthException e) {
            send(ServerMessage.error("unauthorized", e.getMessage()));
            sink.close(CLOSE_POLICY_VIOLATION, "unauthorized");
            return;
        }
        send(ServerMessage.ready(clock.instant(), scope.subject()));
    }

    private void onSubscribe(ClientMessage msg) {
        if (scope == null) {
            send(ServerMessage.error("not_authenticated", "Send an auth message first"));
            return;
        }
        if (msg.bounds() != null && !msg.bounds().isValid()) {
            send(ServerMessage.error("bad_request", "bounds needs n, s, e, w in degrees with n >= s"));
            return;
        }
        devices = msg.devices() == null || msg.devices().isEmpty() ? null : Set.copyOf(msg.devices());
        bounds = msg.bounds();
        subscribed = true;
        pending.clear();
        sendSnapshot();
    }

    private void onBounds(ClientMessage msg) {
        if (!subscribed) {
            send(ServerMessage.error("not_authenticated", "Subscribe before sending bounds"));
            return;
        }
        if (msg.bounds() != null && !msg.bounds().isValid()) {
            send(ServerMessage.error("bad_request", "bounds needs n, s, e, w in degrees with n >= s"));
            return;
        }
        bounds = msg.bounds();
        pending.clear();
        sendSnapshot();
    }

    private void sendSnapshot() {
        send(ServerMessage.snapshot(store.snapshot(this::visible)));
    }

    // ---------------------------------------------------------------- outbound

    /** Called for every new latest position. */
    public synchronized void offer(Position p) {
        if (subscribed && visible(p)) {
            pending.remove(p.deviceUid());       // re-insert so the batch is ordered by arrival
            pending.put(p.deviceUid(), p);
        }
    }

    /** Called for every device event. Events are not viewport-filtered: an alarm off-screen still matters. */
    public synchronized void offer(DeviceEvent e) {
        if (subscribed && scope.canSee().test(e.deviceUid()) && (devices == null || devices.contains(e.deviceUid()))) {
            if (pendingEvents.size() >= MAX_PENDING_EVENTS) {
                pendingEvents.pollFirst();
                droppedEvents++;
            }
            pendingEvents.addLast(e);
        }
    }

    /**
     * Sends everything queued since the last flush. Also closes sessions that never authenticated
     * within the auth timeout, and sessions whose credential has expired.
     */
    public synchronized void flush() {
        if (scope == null) {
            if (sink.isOpen() && !clock.instant().isBefore(openedAt.plus(authTimeout))) {
                send(ServerMessage.error("not_authenticated", "No auth message received in time"));
                sink.close(CLOSE_POLICY_VIOLATION, "auth timeout");
            }
            return;
        }
        if (scope.expiresAt() != null && !clock.instant().isBefore(scope.expiresAt())) {
            send(ServerMessage.error("unauthorized", "Token expired, reconnect with a new token"));
            sink.close(CLOSE_POLICY_VIOLATION, "token expired");
            scope = null;
            subscribed = false;
            return;
        }
        if (!pending.isEmpty()) {
            List<Position> batch = new ArrayList<>(pending.values());
            pending.clear();
            send(ServerMessage.positions(batch));
        }
        if (!pendingEvents.isEmpty()) {
            List<DeviceEvent> batch = new ArrayList<>(pendingEvents);
            pendingEvents.clear();
            send(ServerMessage.events(batch));
        }
    }

    private boolean visible(Position p) {
        return scope != null
                && scope.canSee().test(p.deviceUid())
                && (devices == null || devices.contains(p.deviceUid()))
                && (bounds == null || bounds.contains(p.latitude(), p.longitude()));
    }

    private void send(ServerMessage m) {
        if (sink.isOpen()) {
            sink.send(json.write(m));
        }
    }
}
