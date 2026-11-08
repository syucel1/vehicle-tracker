package com.vehicletracker.bridge.core;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides whether a device is allowed to feed data into the platform.
 * Phase 1 uses a static configuration; later phases back this with the Core API's device registry.
 */
public interface DeviceRegistry {

    boolean isAllowed(String deviceUid);

    enum Mode {
        /** Every device is accepted (development / onboarding). */
        OPEN,
        /** Only listed devices are accepted; others are reported on device.unknown. */
        ALLOW_LIST
    }

    static DeviceRegistry of(Mode mode, Collection<String> allowed) {
        if (mode == Mode.OPEN) {
            return uid -> true;
        }
        Set<String> ids = allowed.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        return ids::contains;
    }
}
