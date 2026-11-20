package com.jia.academy.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordResetService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String resetUrl;
    private final String encryptionSecret;
    private final String fromAddress;
    private final String mailHost;

    public PasswordResetService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${jia.auth.password-reset-url:}") String resetUrl,
            @Value("${jia.auth.reset-encryption-key:}") String encryptionSecret,
            @Value("${SMTP_FROM:}") String fromAddress,
            @Value("${spring.mail.host:}") String mailHost) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.resetUrl = resetUrl;
        this.encryptionSecret = encryptionSecret;
        this.fromAddress = fromAddress;
        this.mailHost = mailHost;
    }

    @Transactional
    public void requestReset(String email) {
        requireDeliveryConfiguration();
        String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
        var users = jdbc.query("SELECT id,email FROM users WHERE lower(email)=? AND status='ACTIVE'",
                (rs, row) -> new ResetUser(rs.getObject("id", UUID.class), rs.getString("email")), normalized);
        if (users.isEmpty()) return;
        ResetUser user = users.getFirst();
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawToken = "jia_reset_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();
        jdbc.update("UPDATE password_reset_tokens SET consumed_at=? WHERE user_id=? AND consumed_at IS NULL",
                Timestamp.from(now), user.id());
        jdbc.update("""
                INSERT INTO password_reset_tokens(id,user_id,token_hash,created_at,expires_at)
                VALUES (?,?,?,?,?)
                """, UUID.randomUUID(), user.id(), sha256(rawToken), Timestamp.from(now),
                Timestamp.from(now.plusSeconds(30 * 60)));
        String separator = resetUrl.contains("?") ? "&" : "?";
        String link = resetUrl + separator + "token="
                + java.net.URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        jdbc.update("""
                INSERT INTO auth_email_outbox(id,recipient,template_key,encrypted_payload,status,available_at)
                VALUES (?,?,?,?,'PENDING',?)
                """, UUID.randomUUID(), user.email(), "PASSWORD_RESET", encrypt(link), Timestamp.from(now));
    }

    @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token");
        }
        String hash = sha256(rawToken);
        var rows = jdbc.query("""
                SELECT id,user_id,expires_at,consumed_at FROM password_reset_tokens
                WHERE token_hash=? FOR UPDATE
                """, (rs, row) -> new ResetToken(rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class), rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("consumed_at") == null ? null : rs.getTimestamp("consumed_at").toInstant()), hash);
        if (rows.isEmpty() || rows.getFirst().consumedAt() != null
                || !rows.getFirst().expiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token");
        }
        ResetToken token = rows.getFirst();
        Timestamp now = Timestamp.from(Instant.now());
        int changed = jdbc.update("UPDATE users SET password_hash=?,updated_at=? WHERE id=? AND status='ACTIVE'",
                passwordEncoder.encode(newPassword), now, token.userId());
        if (changed == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is not active");
        }
        jdbc.update("UPDATE password_reset_tokens SET consumed_at=? WHERE id=?", now, token.id());
        jdbc.update("UPDATE password_reset_tokens SET consumed_at=? WHERE user_id=? AND consumed_at IS NULL",
                now, token.userId());
        jdbc.update("UPDATE api_tokens SET revoked_at=? WHERE user_id=? AND revoked_at IS NULL",
                now, token.userId());
        jdbc.update("UPDATE auth_refresh_tokens SET revoked_at=? WHERE user_id=? AND revoked_at IS NULL",
                now, token.userId());
        jdbc.update("""
                INSERT INTO audit_events(id,actor_user_id,action,entity_type,entity_id,metadata_json,occurred_at)
                VALUES (?,?,?,'USER',?,jsonb_build_object('credential','password-reset'),?)
                """, UUID.randomUUID(), token.userId(), "PASSWORD_RESET_COMPLETED", token.userId(), now);
    }

    @Scheduled(fixedDelayString = "${jia.auth.email-outbox-poll-ms:15000}")
    public void dispatchPasswordResetEmails() {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) return;
        jdbc.update("UPDATE auth_email_outbox SET status='PENDING',processing_at=NULL WHERE status='PROCESSING' AND processing_at < now() - interval '5 minutes'");
        List<OutboxMessage> messages = jdbc.query("""
                UPDATE auth_email_outbox SET status='PROCESSING',processing_at=now(),attempts=attempts+1
                WHERE id=(SELECT id FROM auth_email_outbox
                    WHERE status='PENDING' AND available_at<=now()
                    ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1)
                RETURNING id,recipient,template_key,encrypted_payload,attempts
                """, (rs, row) -> new OutboxMessage(rs.getObject("id", UUID.class),
                rs.getString("recipient"), rs.getString("template_key"),
                rs.getString("encrypted_payload"), rs.getInt("attempts")));
        if (messages.isEmpty()) return;
        OutboxMessage message = messages.getFirst();
        try {
            String link = decrypt(message.encryptedPayload());
            SimpleMailMessage mail = new SimpleMailMessage();
            if (fromAddress != null && !fromAddress.isBlank()) mail.setFrom(fromAddress);
            mail.setTo(message.recipient());
            mail.setSubject("Reset your JIA Academy password");
            mail.setText("A password reset was requested for your JIA Academy account.\n\n"
                    + "Use this one-time link within 30 minutes:\n" + link
                    + "\n\nIf you did not request this change, ignore this email.");
            sender.send(mail);
            jdbc.update("UPDATE auth_email_outbox SET status='SENT',sent_at=now(),processing_at=NULL,last_error=NULL WHERE id=?",
                    message.id());
        } catch (Exception ex) {
            String status = message.attempts() >= 5 ? "FAILED" : "PENDING";
            jdbc.update("""
                    UPDATE auth_email_outbox SET status=?,available_at=now() + interval '2 minutes',
                        processing_at=NULL,last_error=? WHERE id=?
                    """, status, safeError(ex), message.id());
        }
    }

    private void requireDeliveryConfiguration() {
        if (resetUrl == null || resetUrl.isBlank() || encryptionSecret == null || encryptionSecret.length() < 32
                || mailHost == null || mailHost.isBlank() || mailSender.getIfAvailable() == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Password recovery is not configured on this server");
        }
    }

    private String encrypt(String value) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not encrypt password reset message", ex);
        }
    }

    private String decrypt(String value) throws Exception {
        byte[] packed = Base64.getDecoder().decode(value);
        byte[] iv = java.util.Arrays.copyOfRange(packed, 0, 12);
        byte[] encrypted = java.util.Arrays.copyOfRange(packed, 12, packed.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private SecretKeySpec key() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(encryptionSecret.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException("SHA-256 is unavailable", ex); }
    }

    private static String safeError(Exception ex) {
        String message = ex.getMessage() == null ? "Mail delivery failed" : ex.getMessage();
        return message.substring(0, Math.min(500, message.length()));
    }

    private record ResetUser(UUID id, String email) {}
    private record ResetToken(UUID id, UUID userId, Instant expiresAt, Instant consumedAt) {}
    private record OutboxMessage(UUID id, String recipient, String templateKey,
                                 String encryptedPayload, int attempts) {}
}
