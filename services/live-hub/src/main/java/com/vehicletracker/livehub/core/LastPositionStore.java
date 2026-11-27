package com.vehicletracker.livehub.core;

import com.vehicletracker.model.Position;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Latest known position per device. Rebuilt on start-up from the compacted {@code positions.latest}
 * topic. Older fixes never replace newer ones, so buffered data a tracker uploads after reconnecting
 * does not move a vehicle backwards on the live map.
 */
public final class LastPositionStore {

    private final ConcurrentHashMap<String, Position> latest = new ConcurrentHashMap<>();

    /** @return true when the position became the device's latest (and should be pushed to clients) */
    public boolean update(Position p) {
        if (p == null || p.deviceUid() == null || p.fixTime() == null) {
            return false;
        }
        boolean[] changed = {false};
        latest.compute(p.deviceUid(), (uid, current) -> {
            if (current == null || !p.fixTime().isBefore(current.fixTime())) {
                changed[0] = current == null || !p.equals(current);
                return p;
            }
            return current;
        });
        return changed[0];
    }

    public void remove(String deviceUid) {
        latest.remove(deviceUid);
    }

    public Position get(String deviceUid) {
        return latest.get(deviceUid);
    }

    public int size() {
        return latest.size();
    }

    /** Matching positions ordered by device ID (stable order helps clients and tests). */
    public List<Position> snapshot(Predicate<Position> filter) {
        return latest.values().stream()
                .filter(filter)
                .sorted(Comparator.comparing(Position::deviceUid))
                .toList();
    }
}
