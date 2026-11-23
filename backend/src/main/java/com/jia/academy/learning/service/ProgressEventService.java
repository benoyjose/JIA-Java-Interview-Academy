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

    public record EventRequest(UUID eventId, String eventType, Instant occurredAt,
                               String lessonSlug, JsonNode payload) {}
    public record EventReceipt(UUID eventId, String status, Instant occurredAt) {}
    public record BatchReceipt(java.util.List<EventReceipt> events, int acceptedCount, int duplicateCount) {}
}
