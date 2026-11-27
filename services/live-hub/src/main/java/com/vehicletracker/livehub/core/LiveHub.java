package com.vehicletracker.livehub.core;

import com.vehicletracker.model.DeviceEvent;
import com.vehicletracker.model.Position;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/** Fans positions and events out to all connected sessions. One instance per process. */
public final class LiveHub {

    private final LastPositionStore store;
    private final ConcurrentHashMap<String, ClientSession> sessions = new ConcurrentHashMap<>();

    public LiveHub(LastPositionStore store) {
        this.store = store;
    }

    public LastPositionStore store() {
        return store;
    }

    public void register(ClientSession session) {
        sessions.put(session.id(), session);
    }

    public void unregister(String sessionId) {
        sessions.remove(sessionId);
    }

    public ClientSession session(String sessionId) {
        return sessions.get(sessionId);
    }

    public int sessionCount() {
        return sessions.size();
    }

    public Collection<ClientSession> sessions() {
        return sessions.values();
    }

    /** @return true if the position was newer than the device's last one and was forwarded */
    public boolean onPosition(Position p) {
        if (!store.update(p)) {
            return false;
        }
        sessions.values().forEach(s -> s.offer(p));
        return true;
    }

    /** A Kafka tombstone on positions.latest: the device was deleted. */
    public void onDeviceRemoved(String deviceUid) {
        store.remove(deviceUid);
    }

    public void onEvent(DeviceEvent e) {
        if (e != null && e.deviceUid() != null) {
            sessions.values().forEach(s -> s.offer(e));
        }
    }

    public void flushAll() {
        sessions.values().forEach(ClientSession::flush);
    }
}
