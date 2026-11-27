package com.vehicletracker.livehub.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Turns the token a client sends in its first message into an {@link AccessScope}. */
@FunctionalInterface
public interface Authorizer {

    /** @throws AuthException when the token is missing, invalid or expired */
    AccessScope authorize(String token);

    class AuthException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public AuthException(String message) {
            super(message);
        }
    }

    /**
     * Development mode: a single shared token grants access to every device.
     * Used until Keycloak and the Core API exist (roadmap R3.1, R3.4). Never use in production.
     */
    static Authorizer devToken(String expected) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        return token -> {
            if (token == null || !MessageDigest.isEqual(expectedBytes, token.getBytes(StandardCharsets.UTF_8))) {
                throw new AuthException("Invalid token");
            }
            return AccessScope.all("dev", null);
        };
    }
}
