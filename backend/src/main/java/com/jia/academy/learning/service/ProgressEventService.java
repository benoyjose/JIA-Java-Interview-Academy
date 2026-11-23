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
        String type = request.eventType().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("LESSON_STARTED", "LESSON_COMPLETED", "QUESTION_ANSWERED",
                "QUIZ_SUBMITTED", "BOOKMARK_CHANGED", "REVIEW_COMPLETED").contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported progress event type");
        }
        UUID academyId = user.academyIds().getFirst();
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
        // Reject arbitrary learner data; schema intentionally carries only small, non-sensitive event metadata.
        var forbidden = java.util.List.of("answer", "answerKey", "password", "token", "email",
                "mastery", "correctness", "solution", "submission");
        forbidden.forEach(key -> {
            if (payload.has(key)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Progress event payload must not contain sensitive or authoritative field: " + key);
        });
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
        return new EventReceipt(request.eventId(), inserted == 1 ? "ACCEPTED" : "DUPLICATE",
                request.occurredAt());
    }

    public record EventRequest(UUID eventId, String eventType, Instant occurredAt,
                               String lessonSlug, JsonNode payload) {}
    public record EventReceipt(UUID eventId, String status, Instant occurredAt) {}
}
