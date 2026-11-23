package com.jia.academy.learning.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jia.academy.security.CurrentUser;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Idempotent append-only ingestion; this endpoint never accepts client mastery/projection values. */
@Service
public class ProgressEventService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ProgressEventService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public BatchReceipt appendBatch(CurrentUser user, java.util.List<EventRequest> requests) {
        validateBatchSize(requests);
        if (requests.stream().anyMatch(java.util.Objects::isNull)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Batch cannot contain null events");
        }
        java.util.List<EventReceipt> receipts = new java.util.ArrayList<>(requests.size());
        int accepted = 0;
        int duplicates = 0;
        for (EventRequest request : requests) {
            EventReceipt receipt = append(user, request);
            receipts.add(receipt);
            if ("ACCEPTED".equals(receipt.status())) accepted++;
            else duplicates++;
        }
        return new BatchReceipt(receipts, accepted, duplicates);
    }

    @Transactional
    public EventReceipt append(CurrentUser user, EventRequest request) {
        if (request.eventId() == null || request.occurredAt() == null || request.eventType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventId, eventType and occurredAt are required");
        }
        if (request.occurredAt().isAfter(Instant.now().plusSeconds(300))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event timestamp exceeds allowed clock skew");
        }
        if (request.payload() != null && (!request.payload().isObject()
                || request.payload().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 8192)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event payload must be an object up to 8 KiB");
        }
        String type = request.eventType().trim().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("LESSON_STARTED", "LESSON_COMPLETED", "QUESTION_ANSWERED",
                "QUIZ_SUBMITTED", "BOOKMARK_CHANGED", "REVIEW_COMPLETED").contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported progress event type");
        }
        UUID academyId = requireAcademyId(user.academyIds());
        UUID lessonId = null;
        if (request.lessonSlug() != null && !request.lessonSlug().isBlank()) {
            lessonId = jdbc.query("""
                    SELECT l.id FROM lessons l JOIN categories c ON c.id=l.category_id
                    JOIN technologies t ON t.id=c.technology_id
                    WHERE l.slug=? AND l.status='PUBLISHED' AND t.academy_id=?
                    """, (rs, row) -> rs.getObject(1, UUID.class), request.lessonSlug(), academyId)
                    .stream().findFirst().orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Published lesson not found"));
        }
        JsonNode payload = request.payload() == null ? mapper.createObjectNode() : request.payload();
        validatePayload(payload);
        String json;
        try { json = mapper.writeValueAsString(payload); }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid event payload"); }
        int inserted = jdbc.update("""
                INSERT INTO learner_progress_events(event_id,user_id,academy_code,event_type,schema_version,
                    occurred_at,lesson_id,payload)
                VALUES (?,?,? ,?,1,?,?,?::jsonb)
                ON CONFLICT (user_id,academy_code,event_id) DO NOTHING
                """, request.eventId(), user.id(), academyId.toString(), type,
                java.sql.Timestamp.from(request.occurredAt()), lessonId, json);
        if (inserted == 0) {
            Integer identical = jdbc.query("""
                    SELECT 1 FROM learner_progress_events
                    WHERE user_id=? AND academy_code=? AND event_id=? AND event_type=?
                      AND schema_version=1 AND occurred_at=? AND lesson_id IS NOT DISTINCT FROM ?
                      AND payload=?::jsonb
                    """, (rs, row) -> rs.getInt(1), user.id(), academyId.toString(),
                    request.eventId(), type, java.sql.Timestamp.from(request.occurredAt()), lessonId, json)
                    .stream().findFirst().orElse(null);
            if (identical == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Event ID was already used with a different event body");
            }
        }
        return new EventReceipt(request.eventId(), inserted == 1 ? "ACCEPTED" : "DUPLICATE",
                request.occurredAt());
    }


    static void validateBatchSize(java.util.List<EventRequest> requests) {
        if (requests == null || requests.isEmpty() || requests.size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Batch must contain between 1 and 100 progress events");
        }
    }

    static UUID requireAcademyId(java.util.List<UUID> academyIds) {
        if (academyIds == null || academyIds.isEmpty() || academyIds.getFirst() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No academy membership is available for progress events");
        }
        return academyIds.getFirst();
    }

    static void validatePayload(JsonNode payload) {
        if (payload == null || payload.isNull()) return;
        if (!payload.isObject()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event payload must be an object");
        validateNode(payload);
    }

    private static void validateNode(JsonNode node) {
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                String normalized = field.getKey().replaceAll("[^A-Za-z0-9]", "").toLowerCase(java.util.Locale.ROOT);
                if (java.util.List.of("answer", "answerkey", "password", "token", "email", "mastery",
                        "correctness", "correct", "score", "solution", "submission", "credential",
                        "secret", "phone", "authorization").stream().anyMatch(normalized::contains)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Event payload contains a prohibited sensitive or authoritative field");
                }
                validateNode(field.getValue());
            }
        } else if (node.isArray()) {
            node.forEach(ProgressEventService::validateNode);
        }
    }

    @Transactional(readOnly = true)
    public ProgressHistory history(CurrentUser user, int requestedSize, String cursor, String eventType) {
        int size = Math.max(1, Math.min(requestedSize, 100));
        UUID academyId = requireAcademyId(user.academyIds());
        Cursor after = decodeCursor(cursor);
        String normalizedType = eventType == null || eventType.isBlank() ? null : eventType.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalizedType != null && !java.util.Set.of("LESSON_STARTED", "LESSON_COMPLETED", "QUESTION_ANSWERED",
                "QUIZ_SUBMITTED", "BOOKMARK_CHANGED", "REVIEW_COMPLETED").contains(normalizedType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported progress event type");
        }
        var rows = jdbc.query("""
                SELECT e.event_id,e.event_type,e.occurred_at,l.slug
                FROM learner_progress_events e LEFT JOIN lessons l ON l.id=e.lesson_id
                WHERE e.user_id=? AND e.academy_code=? AND (? IS NULL OR e.event_type=?)
                  AND (? IS NULL OR (e.occurred_at,e.event_id) < (?,?))
                ORDER BY e.occurred_at DESC,e.event_id DESC LIMIT ?
                """, (rs, row) -> new HistoryItem(rs.getObject("event_id", UUID.class),
                rs.getString("event_type"), rs.getTimestamp("occurred_at").toInstant(), rs.getString("slug")),
                user.id(), academyId.toString(), normalizedType, normalizedType,
                after == null ? null : after.occurredAt(), after == null ? null : after.occurredAt(),
                after == null ? null : after.eventId(), size + 1);
        boolean hasMore = rows.size() > size;
        var items = hasMore ? java.util.List.copyOf(rows.subList(0, size)) : java.util.List.copyOf(rows);
        String next = hasMore && !items.isEmpty() ? encodeCursor(items.getLast()) : null;
        var counts = jdbc.query("""
                SELECT event_type,COUNT(*) AS total FROM learner_progress_events
                WHERE user_id=? AND academy_code=? GROUP BY event_type
                """, (rs,row) -> new EventCount(rs.getString("event_type"),rs.getLong("total")),
                user.id(), academyId.toString());
        return new ProgressHistory(items, next, size, counts);
    }
    private static String encodeCursor(HistoryItem item) {
        String raw = item.occurredAt().toString() + "|" + item.eventId();
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static Cursor decodeCursor(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String[] parts = new String(java.util.Base64.getUrlDecoder().decode(value), java.nio.charset.StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid progress history cursor"); }
    }
    private record Cursor(Instant occurredAt, UUID eventId) {}
    public record HistoryItem(UUID eventId, String eventType, Instant occurredAt, String lessonSlug) {}
    public record EventCount(String eventType, long count) {}
    public record ProgressHistory(java.util.List<HistoryItem> items, String nextCursor, int pageSize, java.util.List<EventCount> summary) {}

    public record EventRequest(UUID eventId, String eventType, Instant occurredAt,
                               String lessonSlug, JsonNode payload) {}
    public record EventReceipt(UUID eventId, String status, Instant occurredAt) {}
    public record BatchReceipt(java.util.List<EventReceipt> events, int acceptedCount, int duplicateCount) {}
}
