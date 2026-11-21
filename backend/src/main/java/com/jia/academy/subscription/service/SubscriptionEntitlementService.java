package com.jia.academy.subscription.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jia.academy.security.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SubscriptionEntitlementService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final String webhookSecret;

    public SubscriptionEntitlementService(JdbcTemplate jdbc, ObjectMapper mapper,
            @Value("${JIA_PAYMENT_WEBHOOK_SECRET:}") String webhookSecret) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
    }

    @Transactional(readOnly = true)
    public List<Entitlement> entitlements(CurrentUser user, String academyCode) {
        UUID academyId = academyId(user, academyCode);
        return jdbc.query("""
                SELECT entitlement_code,status,valid_until
                FROM academy_entitlements
                WHERE user_id=? AND academy_id=?
                  AND status IN ('ACTIVE','GRACE')
                  AND (valid_until IS NULL OR valid_until > now())
                ORDER BY entitlement_code
                """, (rs, n) -> new Entitlement(rs.getString("entitlement_code"),
                rs.getString("status"), rs.getTimestamp("valid_until") == null ? null
                : rs.getTimestamp("valid_until").toInstant()), user.id(), academyId);
    }

    @Transactional(readOnly = true)
    public boolean hasEntitlement(UUID userId, UUID academyId, String code) {
        return !jdbc.query("""
                SELECT 1 FROM academy_entitlements
                WHERE user_id=? AND academy_id=? AND entitlement_code=?
                  AND status IN ('ACTIVE','GRACE')
                  AND (valid_until IS NULL OR valid_until > now())
                """, (rs, n) -> rs.getInt(1), userId, academyId, code).isEmpty();
    }

    @Transactional
    public WebhookResult acceptWebhook(String providerCode, String signature, String rawBody) {
        if (webhookSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Payment webhook is not configured");
        }
        if (providerCode == null || !providerCode.matches("[A-Z][A-Z0-9_]{1,39}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment provider code");
        }
        verifySignature(signature, rawBody);
        ProviderEvent event;
        try {
            event = mapper.readValue(rawBody, ProviderEvent.class);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid provider event payload");
        }
        if (event.id() == null || event.id().isBlank() || event.id().length() > 180
                || event.type() == null || event.type().length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider event identity is required");
        }
        int inserted = jdbc.update("""
                INSERT INTO payment_provider_events(provider_code,provider_event_id,event_type)
                VALUES (?,?,?) ON CONFLICT DO NOTHING
                """, providerCode, event.id(), event.type());
        if (inserted == 0) return new WebhookResult("DUPLICATE", event.id());

        String status = statusFor(event.type());
        if (status == null) {
            jdbc.update("""
                    UPDATE payment_provider_events SET processing_status='IGNORED',processed_at=now()
                    WHERE provider_code=? AND provider_event_id=?
                    """, providerCode, event.id());
            return new WebhookResult("IGNORED", event.id());
        }
        if (event.subscriptionId() == null || event.subscriptionId().isBlank()
                || event.subscriptionId().length() > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider subscription reference is required");
        }
        var rows = jdbc.query("""
                SELECT id,user_id,academy_id,plan_id FROM academy_subscriptions
                WHERE provider_code=? AND provider_subscription_id=?
                FOR UPDATE
                """, (rs, n) -> new SubscriptionRow(rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class), rs.getObject("academy_id", UUID.class),
                rs.getObject("plan_id", UUID.class)), providerCode, event.subscriptionId());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Provider subscription is not linked to an academy account");
        SubscriptionRow sub = rows.getFirst();
        java.sql.Timestamp periodEnd = null;
        if (event.periodEnd() != null) {
            try { periodEnd = java.sql.Timestamp.from(java.time.Instant.parse(event.periodEnd())); }
            catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid periodEnd timestamp"); }
        }
        jdbc.update("""
                UPDATE academy_subscriptions SET status=?,current_period_end=COALESCE(?,current_period_end),
                    updated_at=now() WHERE id=?
                """, status, periodEnd, sub.id());
        String entitlementStatus = switch (status) {
            case "ACTIVE" -> "ACTIVE";
            case "GRACE" -> "GRACE";
            default -> "REVOKED";
        };
        String code = event.entitlementCode() == null || event.entitlementCode().isBlank()
                ? "PREMIUM" : event.entitlementCode().trim();
        if (!code.matches("[A-Z][A-Z0-9_]{1,79}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid entitlement code");
        }
        jdbc.update("""
                INSERT INTO academy_entitlements(user_id,academy_id,entitlement_code,subscription_id,status,valid_until,updated_at)
                VALUES (?,?,?,?,?,?,now())
                ON CONFLICT(user_id,academy_id,entitlement_code) DO UPDATE SET
                    subscription_id=EXCLUDED.subscription_id,status=EXCLUDED.status,
                    valid_until=EXCLUDED.valid_until,updated_at=now()
                """, sub.userId(), sub.academyId(), code, sub.id(), entitlementStatus, periodEnd);
        jdbc.update("""
                UPDATE payment_provider_events SET processing_status='PROCESSED',processed_at=now()
                WHERE provider_code=? AND provider_event_id=?
                """, providerCode, event.id());
        return new WebhookResult("PROCESSED", event.id());
    }

    private UUID academyId(CurrentUser user, String academyCode) {
        if (academyCode == null || !academyCode.matches("[A-Z][A-Z0-9_]{1,39}")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Academy not found");
        }
        return jdbc.query("""
                SELECT a.id FROM academies a JOIN academy_memberships m ON m.academy_id=a.id
                WHERE a.code=? AND a.status='ACTIVE' AND m.user_id=? AND m.status='ACTIVE'
                """, (rs, n) -> rs.getObject(1, UUID.class), academyCode, user.id())
                .stream().findFirst().orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Academy membership not found"));
    }

    private void verifySignature(String signature, String rawBody) {
        if (signature == null || !signature.matches("sha256=[a-fA-F0-9]{64}") || rawBody == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    signature.toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
            }
        } catch (ResponseStatusException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalStateException("Webhook signature verification failed", ex); }
    }

    private static String statusFor(String type) {
        return switch (type) {
            case "subscription.activated", "subscription.renewed", "payment.succeeded" -> "ACTIVE";
            case "payment.failed" -> "GRACE";
            case "subscription.expired" -> "EXPIRED";
            case "subscription.cancelled" -> "CANCELLED";
            case "payment.refunded" -> "REFUNDED";
            default -> null;
        };
    }

    public record Entitlement(String code, String status, java.time.Instant validUntil) {}
    public record WebhookResult(String status, String eventId) {}
    public record ProviderEvent(String id, String type, String subscriptionId,
            String periodEnd, String entitlementCode) {}
    private record SubscriptionRow(UUID id, UUID userId, UUID academyId, UUID planId) {}
}
