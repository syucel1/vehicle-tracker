package com.vehicletracker.livehub.config;

import com.vehicletracker.livehub.core.AccessScope;
import com.vehicletracker.livehub.core.Authorizer;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.HashSet;
import java.util.List;

/**
 * Validates access tokens from the identity provider (Keycloak, roadmap R3.1) against its JWKS keys.
 * Signature and expiry are checked by the decoder. Which devices the user may see comes from a claim
 * (default {@code vt_devices}); until the Core API exists (R3.4) the claim is set in the IdP.
 */
public class JwtAuthorizer implements Authorizer {

    private final JwtDecoder decoder;
    private final String devicesClaim;

    public JwtAuthorizer(String jwkSetUri, String devicesClaim) {
        if (jwkSetUri == null || jwkSetUri.isBlank()) {
            throw new IllegalStateException("live-hub.auth.jwk-set-uri is required when live-hub.auth.mode=JWT");
        }
        this.decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        this.devicesClaim = devicesClaim;
    }

    @Override
    public AccessScope authorize(String token) {
        if (token == null || token.isBlank()) {
            throw new AuthException("Missing token");
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(token);
        } catch (JwtException e) {
            throw new AuthException("Invalid token");
        }
        List<String> devices = jwt.getClaimAsStringList(devicesClaim);
        if (devices == null || devices.isEmpty()) {
            throw new AuthException("Token grants no devices (claim " + devicesClaim + ")");
        }
        if (devices.contains("*")) {
            return AccessScope.all(jwt.getSubject(), jwt.getExpiresAt());
        }
        return AccessScope.devices(jwt.getSubject(), new HashSet<>(devices), jwt.getExpiresAt());
    }
}
