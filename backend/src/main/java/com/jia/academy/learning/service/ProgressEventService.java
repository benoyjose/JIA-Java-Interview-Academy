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
        validateBatchEventIdentity(requests);
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
        int publishedRevision = 0;
        if (request.lessonSlug() != null && !request.lessonSlug().isBlank()) {
            var published = jdbc.query("""
                    SELECT l.id,r.revision_number FROM lessons l
                    JOIN lesson_revisions r ON r.id=l.published_revision_id
                    JOIN categories c ON c.id=l.category_id JOIN technologies t ON t.id=c.technology_id
                    WHERE l.slug=? AND l.status='PUBLISHED' AND t.academy_id=?
                    """, (rs, row) -> new PublishedLessonRef(rs.getObject("id", UUID.class), rs.getInt("revision_number")),
                    request.lessonSlug(), academyId).stream().findFirst().orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Published lesson not found"));
            lessonId = published.id();
            publishedRevision = published.revision();
        }
        JsonNode payload = request.payload() == null ? mapper.createObjectNode() : request.payload();
        validatePayload(payload);
        validateProjectionEvent(type, request.lessonSlug(), payload);
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
        } else {
            // Ledger insertion and learner-facing projection share this transaction.
            projectAcceptedEvent(user, lessonId, publishedRevision, type, payload);
        }
        return new EventReceipt(request.eventId(), inserted == 1 ? "ACCEPTED" : "DUPLICATE",
                request.occurredAt());
    }


    /** Validate event fields before accepting a ledger record that drives a learner projection. */
    static void validateProjectionEvent(String type, String lessonSlug, JsonNode payload) {
        if (!java.util.Set.of("LESSON_STARTED", "LESSON_COMPLETED", "BOOKMARK_CHANGED").contains(type)) return;
        if (lessonSlug == null || lessonSlug.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projected events require a lessonSlug");
        }
        if ("BOOKMARK_CHANGED".equals(type)) {
            if (!payload.path("bookmarked").isBoolean()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bookmark events require a boolean bookmarked value");
            }
            return;
        }
        JsonNode revision = payload.get("contentRevision");
        JsonNode percent = payload.get("progressPercent");
        JsonNode position = payload.get("lastPosition");
        if (revision == null || !revision.canConvertToInt() || revision.asInt() < 1
                || percent == null || !percent.isNumber()
                || percent.decimalValue().compareTo(java.math.BigDecimal.ZERO) < 0
                || percent.decimalValue().compareTo(java.math.BigDecimal.valueOf(100)) > 0
                || (position != null && !position.isNull() && !position.isObject())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Progress events contain an invalid projection payload");
        }
        if ("LESSON_COMPLETED".equals(type) && percent.decimalValue().compareTo(java.math.BigDecimal.valueOf(100)) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Completion events must report 100 percent");
        }
    }

    private void projectAcceptedEvent(CurrentUser user, UUID lessonId, int publishedRevision,
                                     String type, JsonNode payload) {
        if (lessonId == null) return;
        if ("BOOKMARK_CHANGED".equals(type)) {
            if (payload.path("bookmarked").asBoolean()) {
                jdbc.update("""
                        INSERT INTO learner_bookmarks(user_id,lesson_id,created_at)
                        VALUES (?,?,now()) ON CONFLICT (user_id,lesson_id) DO NOTHING
                        """, user.id(), lessonId);
            } else {
                jdbc.update("DELETE FROM learner_bookmarks WHERE user_id=? AND lesson_id=?", user.id(), lessonId);
            }
            return;
        }
        if (!"LESSON_STARTED".equals(type) && !"LESSON_COMPLETED".equals(type)) return;
        int eventRevision = payload.path("contentRevision").asInt();
        if (eventRevision > publishedRevision) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Progress event refers to a newer lesson revision; rebase and retry");
        }
        java.math.BigDecimal percent = payload.path("progressPercent").decimalValue();
        String position = null;
        JsonNode positionNode = payload.get("lastPosition");
        if (positionNode != null && !positionNode.isNull()) {
            try { position = mapper.writeValueAsString(positionNode); }
            catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid progress position"); }
        }
        jdbc.update("""
                INSERT INTO learner_lesson_progress(user_id,lesson_id,content_revision,status,progress_percent,
                    last_position_json,started_at,completed_at,updated_at)
                VALUES (?,?,?,CASE WHEN ? >= 100 THEN 'COMPLETED' ELSE 'IN_PROGRESS' END,?,?::jsonb,
                    now(),CASE WHEN ? >= 100 THEN now() ELSE NULL END,now())
                ON CONFLICT (user_id,lesson_id) DO UPDATE SET
                    content_revision=GREATEST(learner_lesson_progress.content_revision,EXCLUDED.content_revision),
                    progress_percent=GREATEST(learner_lesson_progress.progress_percent,EXCLUDED.progress_percent),
                    status=CASE WHEN GREATEST(learner_lesson_progress.progress_percent,EXCLUDED.progress_percent)>=100
                        THEN 'COMPLETED' ELSE learner_lesson_progress.status END,
                    last_position_json=CASE WHEN EXCLUDED.progress_percent>=learner_lesson_progress.progress_percent
                        THEN EXCLUDED.last_position_json ELSE learner_lesson_progress.last_position_json END,
                    completed_at=COALESCE(learner_lesson_progress.completed_at,EXCLUDED.completed_at),
                    updated_at=GREATEST(now(),learner_lesson_progress.updated_at + interval '1 microsecond')
                """, user.id(), lessonId, publishedRevision, percent, percent, position, percent);
    }

    private record PublishedLessonRef(UUID id, int revision) {}


    /**
     * Fail fast when one client batch reuses an event UUID for different content.
     * The database constraint still protects cross-request identity; this check
     * prevents partially processing a contradictory batch before the transaction
     * eventually encounters the conflict.
     */
    static void validateBatchEventIdentity(java.util.List<EventRequest> requests) {
        java.util.Map<UUID, EventRequest> seen = new java.util.HashMap<>();
        for (EventRequest request : requests) {
            EventRequest prior = seen.putIfAbsent(request.eventId(), request);
            if (prior != null && !sameEventBody(prior, request)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A batch reused an event ID with different event content");
            }
        }
    }

    private static boolean sameEventBody(EventRequest first, EventRequest second) {
        return java.util.Objects.equals(first.eventType(), second.eventType())
                && java.util.Objects.equals(first.occurredAt(), second.occurredAt())
                && java.util.Objects.equals(first.lessonSlug(), second.lessonSlug())
                && java.util.Objects.equals(first.payload(), second.payload());
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
        if (normalizedType != null && !java.util.Set.of("LESSON_STARTED", "LESSON_COMPLETED", "QUESTION_ANSWERED", "QUIZ_SUBMITTED", "BOOKMARK_CHANGED", "REVIEW_COMPLETED").contains(normalizedType))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported progress event type");
        var rows = jdbc.query("""
                SELECT e.event_id,e.event_type,e.occurred_at,l.slug FROM learner_progress_events e
                LEFT JOIN lessons l ON l.id=e.lesson_id
                WHERE e.user_id=? AND e.academy_code=? AND (? IS NULL OR e.event_type=?)
                  AND (? IS NULL OR (e.occurred_at,e.event_id) < (?,?))
                ORDER BY e.occurred_at DESC,e.event_id DESC LIMIT ?
                """, (rs,row) -> new HistoryItem(rs.getObject("event_id",UUID.class),rs.getString("event_type"),
                rs.getTimestamp("occurred_at").toInstant(),rs.getString("slug")),
                user.id(),academyId.toString(),normalizedType,normalizedType,
                after==null?null:after.occurredAt(),after==null?null:after.occurredAt(),after==null?null:after.eventId(),size+1);
        boolean more=rows.size()>size;
        var items=more?java.util.List.copyOf(rows.subList(0,size)):java.util.List.copyOf(rows);
        String next=more&&!items.isEmpty()?encodeCursor(items.getLast()):null;
        var summary=jdbc.query("SELECT event_type,COUNT(*) total FROM learner_progress_events WHERE user_id=? AND academy_code=? GROUP BY event_type",
                (rs,row)->new EventCount(rs.getString("event_type"),rs.getLong("total")),user.id(),academyId.toString());
        return new ProgressHistory(items,next,size,summary);
    }
    private static String encodeCursor(HistoryItem item) {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString((item.occurredAt()+"|"+item.eventId()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static Cursor decodeCursor(String value) {
        if(value==null||value.isBlank())return null;
        try { String[] p=new String(java.util.Base64.getUrlDecoder().decode(value),java.nio.charset.StandardCharsets.UTF_8).split("\\|",-1);
            if(p.length!=2)throw new IllegalArgumentException();return new Cursor(Instant.parse(p[0]),UUID.fromString(p[1]));
        } catch(RuntimeException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid progress history cursor");}
    }
    private record Cursor(Instant occurredAt,UUID eventId){}
    public record HistoryItem(UUID eventId,String eventType,Instant occurredAt,String lessonSlug){}
    public record EventCount(String eventType,long count){}
    public record ProgressHistory(java.util.List<HistoryItem> items,String nextCursor,int pageSize,java.util.List<EventCount> summary){}

    public record EventRequest(UUID eventId, String eventType, Instant occurredAt,
                               String lessonSlug, JsonNode payload) {}
    public record EventReceipt(UUID eventId, String status, Instant occurredAt) {}
    public record BatchReceipt(java.util.List<EventReceipt> events, int acceptedCount, int duplicateCount) {}
}
