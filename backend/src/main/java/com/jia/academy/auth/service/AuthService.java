package com.jia.academy.auth.service;

import com.jia.academy.auth.api.AuthDtos.*;
import com.jia.academy.security.CurrentUser;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long TOKEN_DAYS = 30;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public AuthService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        UUID userId = UUID.randomUUID();
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        try {
            jdbc.update("""
                INSERT INTO users(id,name,email,password_hash,status)
                VALUES (?,?,?,?, 'ACTIVE')
                """, userId, request.name().trim(), email, passwordEncoder.encode(request.password()));
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        jdbc.update("""
            INSERT INTO user_roles(user_id, role_id)
            SELECT ?, id FROM roles WHERE code = 'LEARNER'
            """, userId);
        return issueToken(userId, request.name().trim(), email, List.of("LEARNER"), "registration");
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        var rows = jdbc.query("""
            SELECT id, name, email, password_hash, status
            FROM users WHERE lower(email) = ?
            """, (rs, rowNum) -> new UserCredential(
                rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("email"),
                rs.getString("password_hash"), rs.getString("status")
            ), email);
        if (rows.isEmpty() || !"ACTIVE".equals(rows.getFirst().status())
                || !passwordEncoder.matches(request.password(), rows.getFirst().passwordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        UserCredential user = rows.getFirst();
        List<String> roles = jdbc.queryForList("""
            SELECT r.code FROM roles r JOIN user_roles ur ON ur.role_id=r.id
            WHERE ur.user_id=? ORDER BY r.code
            """, String.class, user.id());
        return issueToken(user.id(), user.name(), user.email(), roles,
                request.deviceName() == null || request.deviceName().isBlank() ? "mobile" : request.deviceName().trim());
    }

    @Transactional
    public TokenResponse authenticateFederated(String provider, String subject, String providerEmail,
                                                boolean emailVerified, String displayName, String deviceName) {
        var existing = jdbc.query("""
            SELECT u.id, u.name, u.email, u.status
            FROM auth_identities ai JOIN users u ON u.id = ai.user_id
            WHERE ai.provider = ? AND ai.provider_subject = ?
            """, (rs, rowNum) -> new FederatedUser(
                rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("email"), rs.getString("status")), provider, subject);
        FederatedUser user;
        if (!existing.isEmpty()) {
            user = existing.getFirst();
            if (!"ACTIVE".equals(user.status())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is not active");
            }
            jdbc.update("UPDATE auth_identities SET last_authenticated_at=? WHERE provider=? AND provider_subject=?",
                    Instant.now(), provider, subject);
        } else {
            String normalizedEmail = providerEmail == null || providerEmail.isBlank()
                    ? null : providerEmail.trim().toLowerCase(java.util.Locale.ROOT);
            if (normalizedEmail != null) {
                if (!emailVerified) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Provider email is not verified");
                }
                Boolean emailExists = jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM users WHERE lower(email)=?)",
                        Boolean.class, normalizedEmail);
                if (Boolean.TRUE.equals(emailExists)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "An account already uses this email. Sign in using that method and link the provider from account settings.");
                }
            }
            UUID userId = UUID.randomUUID();
            String safeName = displayName == null || displayName.isBlank()
                    ? "JIA Learner" : displayName.trim().substring(0, Math.min(160, displayName.trim().length()));
            jdbc.update("""
                INSERT INTO users(id,name,email,password_hash,status)
                VALUES (?,?,?,NULL,'ACTIVE')
                """, userId, safeName, normalizedEmail);
            jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code='LEARNER'", userId);
            jdbc.update("""
                INSERT INTO auth_identities(id,user_id,provider,provider_subject,provider_email,email_verified,created_at,last_authenticated_at)
                VALUES (?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), userId, provider, subject, normalizedEmail, emailVerified, Instant.now(), Instant.now());
            user = new FederatedUser(userId, safeName, normalizedEmail, "ACTIVE");
        }
        List<String> roles = jdbc.queryForList("""
            SELECT r.code FROM roles r JOIN user_roles ur ON ur.role_id=r.id
            WHERE ur.user_id=? ORDER BY r.code
            """, String.class, user.id());
        return issueToken(user.id(), user.name(), user.email(), roles,
                deviceName == null || deviceName.isBlank() ? "mobile" : deviceName.trim());
    }

    @Transactional
    public TokenResponse authenticatePhone(String phoneE164, String deviceName) {
        var existing = jdbc.query("""
            SELECT u.id, u.name, u.email, u.status
            FROM auth_identities ai JOIN users u ON u.id=ai.user_id
            WHERE ai.provider='PHONE' AND ai.provider_subject=?
            """, (rs, rowNum) -> new FederatedUser(
                rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("email"), rs.getString("status")), phoneE164);
        FederatedUser user;
        if (!existing.isEmpty()) {
            user = existing.getFirst();
            if (!"ACTIVE".equals(user.status())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is not active");
            }
            jdbc.update("UPDATE auth_identities SET last_authenticated_at=? WHERE provider='PHONE' AND provider_subject=?",
                    Instant.now(), phoneE164);
        } else {
            UUID userId = UUID.randomUUID();
            String name = "JIA Learner";
            jdbc.update("INSERT INTO users(id,name,email,password_hash,status) VALUES (?,?,NULL,NULL,'ACTIVE')",
                    userId, name);
            jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code='LEARNER'", userId);
            jdbc.update("""
                INSERT INTO auth_identities(id,user_id,provider,provider_subject,provider_email,email_verified,created_at,last_authenticated_at)
                VALUES (?,?,'PHONE',?,NULL,FALSE,?,?)
                """, UUID.randomUUID(), userId, phoneE164, Instant.now(), Instant.now());
            user = new FederatedUser(userId, name, null, "ACTIVE");
        }
        List<String> roles = jdbc.queryForList("""
            SELECT r.code FROM roles r JOIN user_roles ur ON ur.role_id=r.id
            WHERE ur.user_id=? ORDER BY r.code
            """, String.class, user.id());
        return issueToken(user.id(), user.name(), user.email(), roles,
                deviceName == null || deviceName.isBlank() ? "mobile" : deviceName);
    }

    @Transactional
    public void logout(String rawToken) {
        jdbc.update("UPDATE api_tokens SET revoked_at=? WHERE token_hash=? AND revoked_at IS NULL",
                Instant.now(), sha256(rawToken));
    }

    public UserResponse currentUser(CurrentUser user) {
        return new UserResponse(user.id(), user.name(), user.email(), user.roles());
    }

    private TokenResponse issueToken(UUID userId, String name, String email, List<String> roles, String device) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawToken = "jia_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();
        Instant expires = now.plusSeconds(TOKEN_DAYS * 24 * 60 * 60);
        jdbc.update("""
            INSERT INTO api_tokens(id,user_id,token_hash,token_name,created_at,expires_at)
            VALUES (?,?,?,?,?,?)
            """, UUID.randomUUID(), userId, sha256(rawToken), device.substring(0, Math.min(100, device.length())), now, expires);
        return new TokenResponse(rawToken, "Bearer", expires, new UserResponse(userId, name, email, roles));
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private record FederatedUser(UUID id, String name, String email, String status) {}

    private record UserCredential(UUID id, String name, String email, String passwordHash, String status) {}
}
