package com.jia.academy.auth.service;

import com.jia.academy.auth.api.AuthDtos.TokenResponse;
import java.net.URI;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PhoneOtpService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final AuthService auth;
    private final RestClient restClient;
    private final String endpoint;
    private final String token;
    private final String hmacSecret;

    public PhoneOtpService(JdbcTemplate jdbc, AuthService auth, RestClient.Builder builder,
            @Value("${jia.sms.endpoint:}") String endpoint,
            @Value("${jia.sms.token:}") String token,
            @Value("${jia.otp.hmac-secret:}") String hmacSecret) {
        this.jdbc = jdbc;
        this.auth = auth;
        this.restClient = builder.build();
        this.endpoint = endpoint;
        this.token = token;
        this.hmacSecret = hmacSecret;
    }

    public void requestCode(String phone) {
        requireConfigured();
        Integer recent = jdbc.queryForObject("""
            SELECT count(*) FROM phone_otp_challenges
            WHERE phone_e164=? AND created_at >= now() - interval '1 hour'
            """, Integer.class, phone);
        if (recent != null && recent >= 5) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many code requests. Try again later.");
        }

        int code = 100000 + RANDOM.nextInt(900000);
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO phone_otp_challenges(id,phone_e164,code_digest,expires_at,attempts,created_at)
            VALUES (?,?,?,?,0,now())
            """, id, phone, digest(id, phone, Integer.toString(code)), java.time.Instant.now().plusSeconds(300));
        try {
            restClient.post().uri(URI.create(endpoint)).headers(h -> h.setBearerAuth(token))
                .body(Map.of("to", phone, "message",
                    "Your JIA verification code is " + code + ". It expires in 5 minutes."))
                .retrieve().toBodilessEntity();
        } catch (RuntimeException ex) {
            jdbc.update("DELETE FROM phone_otp_challenges WHERE id=?", id);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SMS delivery is temporarily unavailable");
        }
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public TokenResponse verify(String phone, String code, String deviceName) {
        requireConfigured();
        var rows = jdbc.query("""
            SELECT id,code_digest,attempts FROM phone_otp_challenges
            WHERE phone_e164=? AND consumed_at IS NULL AND expires_at > now()
            ORDER BY created_at DESC LIMIT 1 FOR UPDATE
            """, (rs, n) -> new Challenge(rs.getObject("id", UUID.class),
                rs.getString("code_digest"), rs.getInt("attempts")), phone);
        if (rows.isEmpty() || rows.getFirst().attempts() >= 5) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired verification code");
        }
        Challenge challenge = rows.getFirst();
        jdbc.update("UPDATE phone_otp_challenges SET attempts=attempts+1 WHERE id=?", challenge.id());
        if (!MessageDigest.isEqual(challenge.digest().getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                digest(challenge.id(), phone, code).getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired verification code");
        }
        jdbc.update("UPDATE phone_otp_challenges SET consumed_at=now() WHERE id=?", challenge.id());
        return auth.authenticatePhone(phone, deviceName);
    }

    private void requireConfigured() {
        if (endpoint.isBlank() || token.isBlank() || hmacSecret.length() < 32) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Phone sign-in is not configured on this server");
        }
    }

    private String digest(UUID id, String phone, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((id + "|" + phone + "|" + code)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("OTP digest is unavailable", ex);
        }
    }

    private record Challenge(UUID id, String digest, int attempts) {}
}
