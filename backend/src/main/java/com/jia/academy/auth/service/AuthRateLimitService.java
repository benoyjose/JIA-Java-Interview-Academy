package com.jia.academy.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Database-backed login throttling shared by all Spring Boot instances. */
@Service
public class AuthRateLimitService {
    private static final int MAX_ATTEMPTS = 10;
    private final JdbcTemplate jdbc;

    public AuthRateLimitService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void checkAndRecord(String remoteAddress, String email) {
        if (remoteAddress == null || remoteAddress.isBlank()) remoteAddress = "unknown";
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
        int ipAttempts = record(hash("ip|" + remoteAddress));
        int accountAttempts = record(hash("account|" + remoteAddress + "|" + normalizedEmail));
        if (ipAttempts > MAX_ATTEMPTS || accountAttempts > MAX_ATTEMPTS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many sign-in attempts. Wait 15 minutes and try again.");
        }
    }

    public void clear(String remoteAddress, String email) {
        if (remoteAddress == null || remoteAddress.isBlank()) remoteAddress = "unknown";
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
        // Preserve the shared IP window; a successful login by another account must not reset it.
        jdbc.update("DELETE FROM auth_login_rate_limits WHERE key_hash=?",
                hash("account|" + remoteAddress + "|" + normalizedEmail));
    }

    private int record(String keyHash) {
        return jdbc.queryForObject("""
                INSERT INTO auth_login_rate_limits(key_hash,window_started_at,attempt_count,updated_at)
                VALUES (?,now(),1,now())
                ON CONFLICT(key_hash) DO UPDATE SET
                    window_started_at=CASE
                        WHEN auth_login_rate_limits.window_started_at < now() - interval '15 minutes'
                        THEN now() ELSE auth_login_rate_limits.window_started_at END,
                    attempt_count=CASE
                        WHEN auth_login_rate_limits.window_started_at < now() - interval '15 minutes'
                        THEN 1 ELSE auth_login_rate_limits.attempt_count + 1 END,
                    updated_at=now()
                RETURNING attempt_count
                """, Integer.class, keyHash);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
