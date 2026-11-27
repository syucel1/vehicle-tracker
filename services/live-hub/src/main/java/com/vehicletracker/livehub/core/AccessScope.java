package com.vehicletracker.livehub.core;

import java.time.Instant;
import java.util.Set;
import java.util.function.Predicate;

/**
 * What an authenticated client may see.
 *
 * @param subject   user identifier (for logs and metrics)
 * @param canSee    true for device unique IDs the user is allowed to receive
 * @param expiresAt when the credential expires; the session is closed then (null = no expiry)
 */
public record AccessScope(String subject, Predicate<String> canSee, Instant expiresAt) {

    public static AccessScope all(String subject, Instant expiresAt) {
        return new AccessScope(subject, uid -> true, expiresAt);
    }

    public static AccessScope devices(String subject, Set<String> deviceUids, Instant expiresAt) {
        Set<String> copy = Set.copyOf(deviceUids);
        return new AccessScope(subject, copy::contains, expiresAt);
    }
}
