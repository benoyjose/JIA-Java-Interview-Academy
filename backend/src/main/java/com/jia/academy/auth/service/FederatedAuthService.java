package com.jia.academy.auth.service;

import com.jia.academy.auth.api.AuthDtos.TokenResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FederatedAuthService {
    private final JwtDecoder googleDecoder;
    private final JwtDecoder appleDecoder;
    private final AuthService auth;
    private final String googleAudience;
    private final String appleAudience;

    public FederatedAuthService(
            @Qualifier("googleIdTokenDecoder") JwtDecoder googleDecoder,
            @Qualifier("appleIdTokenDecoder") JwtDecoder appleDecoder,
            AuthService auth,
            @Value("${jia.auth.google.client-id:}") String googleAudience,
            @Value("${jia.auth.apple.client-id:}") String appleAudience) {
        this.googleDecoder = googleDecoder;
        this.appleDecoder = appleDecoder;
        this.auth = auth;
        this.googleAudience = googleAudience;
        this.appleAudience = appleAudience;
    }

    public TokenResponse google(String idToken, String nonce, String deviceName) {
        Jwt jwt = decode(googleDecoder, idToken);
        requireAudience(jwt, googleAudience);
        requireNonce(jwt, nonce, false);
        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        Boolean verified = jwt.getClaim("email_verified");
        if (subject == null || email == null || !Boolean.TRUE.equals(verified)) {
            throw unauthorized();
        }
        return auth.authenticateFederated("GOOGLE", subject, email, true,
                jwt.getClaimAsString("name"), deviceName);
    }

    public TokenResponse apple(String identityToken, String nonce, String deviceName) {
        Jwt jwt = decode(appleDecoder, identityToken);
        requireAudience(jwt, appleAudience);
        requireNonce(jwt, nonce, true);
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw unauthorized();
        }
        String email = jwt.getClaimAsString("email");
        Boolean verifiedClaim = jwt.getClaim("email_verified");
        boolean verified = Boolean.TRUE.equals(verifiedClaim)
                || "true".equalsIgnoreCase(String.valueOf(verifiedClaim));
        return auth.authenticateFederated("APPLE", subject, email, verified,
                null, deviceName);
    }

    private Jwt decode(JwtDecoder decoder, String token) {
        if (token == null || token.isBlank() || token.length() > 12000) {
            throw unauthorized();
        }
        try {
            return decoder.decode(token);
        } catch (Exception ex) {
            throw unauthorized();
        }
    }

    private void requireAudience(Jwt jwt, String configuredAudience) {
        if (configuredAudience == null || configuredAudience.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Federated sign-in is not configured on this server");
        }
        List<String> audiences = jwt.getAudience();
        if (audiences == null || !audiences.contains(configuredAudience)) {
            throw unauthorized();
        }
    }

    private void requireNonce(Jwt jwt, String suppliedNonce, boolean apple) {
        if (suppliedNonce == null || suppliedNonce.isBlank()) {
            throw unauthorized();
        }
        String expected = apple ? sha256(suppliedNonce) : suppliedNonce;
        if (!expected.equals(jwt.getClaimAsString("nonce"))) {
            throw unauthorized();
        }
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid federated identity token");
    }
}
