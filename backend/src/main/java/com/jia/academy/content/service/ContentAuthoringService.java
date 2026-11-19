package com.jia.academy.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jia.academy.content.api.ContentAuthoringDtos.*;
import com.jia.academy.security.CurrentUser;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ContentAuthoringService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ContentAuthoringService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public LessonDraftResponse createDraft(CurrentUser actor, LessonDraftRequest request) {
        validateContent(request.contentJson());
        validateTaxonomy(request.difficulty(), request.sourceType());
        UUID categoryId = jdbc.query(
                "SELECT id FROM categories WHERE slug=?",
                (rs, row) -> rs.getObject("id", UUID.class), request.categorySlug())
                .stream().findFirst().orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        UUID lessonId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO lessons(id,category_id,slug,title,summary,difficulty,content_json,
                        content_revision,source_type,status)
                    VALUES (?,?,?,?,?,?,?::jsonb,1,?,'DRAFT')
                    """, lessonId, categoryId, request.slug().trim(), request.title().trim(),
                    request.summary(), request.difficulty().toUpperCase(), request.contentJson(),
                    request.sourceType().toUpperCase());
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A lesson with this slug already exists");
        }
        jdbc.update("""
                INSERT INTO lesson_revisions(id,lesson_id,revision_number,title,summary,difficulty,
                    content_json,source_type,changed_by,change_note)
                VALUES (?,?,1,?,?,?,?::jsonb,?,?,?)
                """, revisionId, lessonId, request.title().trim(), request.summary(),
                request.difficulty().toUpperCase(), request.contentJson(), request.sourceType().toUpperCase(),
                actor.id(), request.changeNote());
        audit(actor, "LESSON_DRAFT_CREATED", lessonId, Map.of("revision", 1));
        return new LessonDraftResponse(lessonId, request.slug().trim(), request.title().trim(), "DRAFT", 1);
    }

    @Transactional
    public LessonDraftResponse updateDraft(CurrentUser actor, UUID lessonId, LessonDraftRequest request) {
        validateContent(request.contentJson());
        validateTaxonomy(request.difficulty(), request.sourceType());
        Integer currentRevision = jdbc.query(
                "SELECT content_revision FROM lessons WHERE id=? AND status='DRAFT'",
                (rs, row) -> rs.getInt(1), lessonId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Only draft lessons can be edited in this workflow"));
        UUID categoryId = jdbc.query(
                "SELECT id FROM categories WHERE slug=?",
                (rs, row) -> rs.getObject("id", UUID.class), request.categorySlug())
                .stream().findFirst().orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        int nextRevision = currentRevision + 1;
        try {
            jdbc.update("""
                    UPDATE lessons SET category_id=?,slug=?,title=?,summary=?,difficulty=?,content_json=?::jsonb,
                        content_revision=?,source_type=?,updated_at=now()
                    WHERE id=? AND status='DRAFT'
                    """, categoryId, request.slug().trim(), request.title().trim(), request.summary(),
                    request.difficulty().toUpperCase(), request.contentJson(), nextRevision,
                    request.sourceType().toUpperCase(), lessonId);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A lesson with this slug already exists");
        }
        jdbc.update("""
                INSERT INTO lesson_revisions(id,lesson_id,revision_number,title,summary,difficulty,
                    content_json,source_type,changed_by,change_note)
                VALUES (?,?,?,?,?,?,?::jsonb,?,?,?)
                """, UUID.randomUUID(), lessonId, nextRevision, request.title().trim(), request.summary(),
                request.difficulty().toUpperCase(), request.contentJson(), request.sourceType().toUpperCase(),
                actor.id(), request.changeNote());
        audit(actor, "LESSON_DRAFT_UPDATED", lessonId, Map.of("revision", nextRevision));
        return new LessonDraftResponse(lessonId, request.slug().trim(), request.title().trim(), "DRAFT", nextRevision);
    }

    @Transactional
    public Map<String, Object> submitForReview(CurrentUser actor, UUID lessonId) {
        var revisions = jdbc.query("""
                SELECT content_revision FROM lessons WHERE id=? AND status='DRAFT'
                """, (rs, row) -> rs.getInt(1), lessonId);
        if (revisions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft lessons can be submitted");
        }
        int revision = revisions.getFirst();
        UUID revisionId = jdbc.queryForObject(
                "SELECT id FROM lesson_revisions WHERE lesson_id=? AND revision_number=?",
                UUID.class, lessonId, revision);
        jdbc.update("UPDATE lessons SET status='IN_REVIEW',updated_at=now() WHERE id=?", lessonId);
        UUID taskId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO content_review_tasks(id,lesson_id,revision_id,submitted_by,status)
                VALUES (?,?,?,?,'PENDING')
                """, taskId, lessonId, revisionId, actor.id());
        audit(actor, "LESSON_SUBMITTED_FOR_REVIEW", lessonId, Map.of("revision", revision));
        return Map.of("taskId", taskId, "lessonId", lessonId, "revisionId", revisionId, "status", "PENDING");
    }

    @Transactional(readOnly = true)
    public List<ReviewQueueItem> reviewQueue() {
        return jdbc.query("""
                SELECT t.id AS task_id,l.id AS lesson_id,r.id AS revision_id,l.slug,r.title,
                       u.name AS submitted_by,t.status,t.submitted_at
                FROM content_review_tasks t
                JOIN lessons l ON l.id=t.lesson_id
                JOIN lesson_revisions r ON r.id=t.revision_id
                JOIN users u ON u.id=t.submitted_by
                WHERE t.status='PENDING'
                ORDER BY t.submitted_at ASC
                """, (rs, row) -> new ReviewQueueItem(
                rs.getObject("task_id", UUID.class), rs.getObject("lesson_id", UUID.class),
                rs.getObject("revision_id", UUID.class), rs.getString("slug"), rs.getString("title"),
                rs.getString("submitted_by"), rs.getString("status"),
                rs.getTimestamp("submitted_at").toInstant()));
    }

    @Transactional
    public Map<String, Object> decideReview(CurrentUser actor, UUID taskId, ReviewDecisionRequest request) {
        String decision = request.decision().trim().toUpperCase();
        if (!List.of("APPROVE", "REQUEST_CHANGES", "REJECT").contains(decision)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Decision must be APPROVE, REQUEST_CHANGES or REJECT");
        }
        var tasks = jdbc.query("""
                SELECT lesson_id,submitted_by FROM content_review_tasks WHERE id=? AND status='PENDING'
                """, (rs, row) -> new ReviewTaskOwner(
                        rs.getObject("lesson_id", UUID.class), rs.getObject("submitted_by", UUID.class)), taskId);
        if (tasks.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pending review task not found");
        }
        UUID lessonId = tasks.getFirst().lessonId();
        if (tasks.getFirst().submittedBy().equals(actor.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authors cannot review their own submissions");
        }
        String taskStatus = switch (decision) {
            case "APPROVE" -> "APPROVED";
            case "REQUEST_CHANGES" -> "CHANGES_REQUESTED";
            default -> "REJECTED";
        };
        String lessonStatus = "APPROVE".equals(decision) ? "APPROVED" : "DRAFT";
        jdbc.update("""
                UPDATE content_review_tasks SET status=?,review_comment=?,reviewed_by=?,reviewed_at=?
                WHERE id=? AND status='PENDING'
                """, taskStatus, request.comment(), actor.id(), Timestamp.from(Instant.now()), taskId);
        jdbc.update("UPDATE lessons SET status=?,updated_at=now() WHERE id=?", lessonStatus, lessonId);
        audit(actor, "LESSON_REVIEW_" + decision, lessonId, Map.of("taskId", taskId.toString()));
        return Map.of("taskId", taskId, "lessonId", lessonId, "status", taskStatus);
    }

    @Transactional
    public Map<String, Object> publish(CurrentUser actor, UUID lessonId) {
        int changed = jdbc.update("""
                UPDATE lessons SET status='PUBLISHED',updated_at=now()
                WHERE id=? AND status='APPROVED'
                """, lessonId);
        if (changed == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only approved lessons can be published");
        }
        audit(actor, "LESSON_PUBLISHED", lessonId, Map.of());
        return Map.of("lessonId", lessonId, "status", "PUBLISHED");
    }

    private record ReviewTaskOwner(UUID lessonId, UUID submittedBy) {}

    private void validateContent(String contentJson) {
        try {
            JsonNode root = mapper.readTree(contentJson);
            if (root == null || !root.isObject() || !root.has("schemaVersion")
                    || !root.path("blocks").isArray()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Content must include schemaVersion and a blocks array");
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content JSON is invalid");
        }
    }

    private void validateTaxonomy(String difficulty, String sourceType) {
        if (!List.of("BEGINNER", "NOVICE", "PROFESSIONAL", "EXPERT").contains(difficulty.toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported lesson difficulty");
        }
        if (!List.of("USER_ORIGINAL", "ADMIN_AUTHORED", "EXTERNAL_REFERENCE").contains(sourceType.toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported source type");
        }
    }

    private void audit(CurrentUser actor, String action, UUID lessonId, Map<String, Object> metadata) {
        try {
            jdbc.update("""
                    INSERT INTO audit_events(id,actor_user_id,academy_id,action,entity_type,entity_id,metadata_json)
                    SELECT ?,?,t.academy_id,?,'LESSON',?,?::jsonb
                    FROM lessons l JOIN categories c ON c.id=l.category_id
                    JOIN technologies t ON t.id=c.technology_id WHERE l.id=?
                    """, UUID.randomUUID(), actor.id(), action, lessonId,
                    mapper.writeValueAsString(metadata), lessonId);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not record content action", ex);
        }
    }
}
