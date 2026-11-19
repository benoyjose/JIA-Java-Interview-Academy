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
        String currentStatus = jdbc.query(
                "SELECT status FROM lessons WHERE id=?",
                (rs, row) -> rs.getString(1), lessonId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found"));
        if ("PUBLISHED".equals(currentStatus)) {
            return updatePublishedDraft(actor, lessonId, request);
        }
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
        var lessons = jdbc.query("""
                SELECT status, content_revision, draft_revision_id
                FROM lessons WHERE id=? FOR UPDATE
                """, (rs, row) -> new LessonReviewState(rs.getString("status"),
                        rs.getInt("content_revision"), rs.getObject("draft_revision_id", UUID.class)), lessonId);
        if (lessons.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found");
        }
        LessonReviewState lesson = lessons.getFirst();
        UUID revisionId;
        int revision;
        if ("DRAFT".equals(lesson.status())) {
            revision = lesson.contentRevision();
            revisionId = jdbc.queryForObject(
                    "SELECT id FROM lesson_revisions WHERE lesson_id=? AND revision_number=?",
                    UUID.class, lessonId, revision);
            jdbc.update("UPDATE lessons SET status='IN_REVIEW',updated_at=now() WHERE id=?", lessonId);
        } else if ("PUBLISHED".equals(lesson.status()) && lesson.draftRevisionId() != null) {
            revisionId = lesson.draftRevisionId();
            revision = jdbc.queryForObject(
                    "SELECT revision_number FROM lesson_revisions WHERE id=?",
                    Integer.class, revisionId);
        } else {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a draft or a prepared published-lesson revision can be submitted");
        }
        Integer pending = jdbc.queryForObject(
                "SELECT COUNT(*) FROM content_review_tasks WHERE lesson_id=? AND status='PENDING'",
                Integer.class, lessonId);
        if (pending != null && pending > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A lesson revision is already under review");
        }
        UUID taskId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO content_review_tasks(id,lesson_id,revision_id,submitted_by,status)
                VALUES (?,?,?,?,'PENDING')
                """, taskId, lessonId, revisionId, actor.id());
        audit(actor, "LESSON_SUBMITTED_FOR_REVIEW", lessonId, Map.of("revision", revision));
        return Map.of("taskId", taskId, "lessonId", lessonId, "revisionId", revisionId, "status", "PENDING");
    }

    @Transactional(readOnly = true)
    public PageResult<LessonAdminListItem> adminLessons(String status, String query, int page, int size) {
        validatePage(query, page, size);
        if (status != null && !List.of("DRAFT", "IN_REVIEW", "APPROVED", "PUBLISHED", "ARCHIVED").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported lesson status filter");
        }
        String search = normalizeSearch(query);
        String pattern = search == null ? null : "%" + search + "%";
        long totalItems = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM lessons l JOIN categories c ON c.id=l.category_id
                WHERE (? IS NULL OR l.status=?)
                  AND (CAST(? AS text) IS NULL OR l.title ILIKE ? OR l.slug ILIKE ? OR c.slug ILIKE ?)
                """, Long.class, status, status, search, pattern, pattern, pattern);
        long offset = (long) page * size;
        List<LessonAdminListItem> items = jdbc.query("""
                SELECT l.id,l.slug,l.title,c.slug AS category_slug,l.status,l.content_revision,
                       (SELECT revision_number FROM lesson_revisions WHERE id=l.published_revision_id) AS published_revision,
                       (SELECT revision_number FROM lesson_revisions WHERE id=l.draft_revision_id) AS draft_revision,
                       l.updated_at
                FROM lessons l JOIN categories c ON c.id=l.category_id
                WHERE (? IS NULL OR l.status=?)
                  AND (CAST(? AS text) IS NULL OR l.title ILIKE ? OR l.slug ILIKE ? OR c.slug ILIKE ?)
                ORDER BY l.updated_at DESC,l.id DESC
                LIMIT ? OFFSET ?
                """, (rs, row) -> new LessonAdminListItem(
                rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("title"),
                rs.getString("category_slug"), rs.getString("status"), rs.getInt("content_revision"),
                (Integer) rs.getObject("published_revision"), (Integer) rs.getObject("draft_revision"),
                rs.getTimestamp("updated_at").toInstant()),
                status, status, search, pattern, pattern, pattern, size, offset);
        return new PageResult<>(items, page, size, totalItems, totalPages(totalItems, size));
    }

    @Transactional(readOnly = true)
    public LessonAuthoringDetail authoringDetail(UUID lessonId) {
        return jdbc.query("""
                SELECT l.id,l.slug,l.status,l.updated_at,c.slug AS category_slug,
                       r.title,r.summary,r.difficulty,r.source_type,r.content_json::text,
                       r.revision_number,r.change_note,
                       (SELECT revision_number FROM lesson_revisions WHERE id=l.published_revision_id) AS published_revision,
                       (l.status <> 'PUBLISHED' OR l.draft_revision_id IS NOT NULL) AS editing_draft
                FROM lessons l
                JOIN categories c ON c.id=l.category_id
                JOIN lesson_revisions r ON r.id=CASE
                    WHEN l.status='PUBLISHED' AND l.draft_revision_id IS NOT NULL THEN l.draft_revision_id
                    WHEN l.status='PUBLISHED' THEN l.published_revision_id
                    ELSE (SELECT id FROM lesson_revisions
                          WHERE lesson_id=l.id AND revision_number=l.content_revision)
                END
                WHERE l.id=?
                """, (rs, row) -> new LessonAuthoringDetail(
                rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("title"),
                rs.getString("summary"), rs.getString("category_slug"), rs.getString("difficulty"),
                rs.getString("source_type"), rs.getString("content_json"), rs.getString("status"),
                rs.getInt("revision_number"), (Integer) rs.getObject("published_revision"),
                rs.getBoolean("editing_draft"), rs.getString("change_note"),
                rs.getTimestamp("updated_at").toInstant()), lessonId)
                .stream().findFirst().orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found"));
    }

    @Transactional(readOnly = true)
    public ReviewDetail reviewDetail(UUID taskId) {
        return jdbc.query("""
                SELECT t.id AS task_id,l.id AS lesson_id,r.id AS revision_id,l.slug,
                       r.title,r.summary,r.difficulty,r.source_type,r.content_json::text,
                       r.revision_number,r.change_note,u.name AS submitted_by,
                       t.submitted_at,l.status AS lesson_status
                FROM content_review_tasks t
                JOIN lessons l ON l.id=t.lesson_id
                JOIN lesson_revisions r ON r.id=t.revision_id
                JOIN users u ON u.id=t.submitted_by
                WHERE t.id=? AND t.status='PENDING'
                """, (rs, row) -> new ReviewDetail(
                rs.getObject("task_id", UUID.class), rs.getObject("lesson_id", UUID.class),
                rs.getObject("revision_id", UUID.class), rs.getString("slug"), rs.getString("title"),
                rs.getString("summary"), rs.getString("difficulty"), rs.getString("source_type"),
                rs.getString("content_json"), rs.getInt("revision_number"), rs.getString("change_note"),
                rs.getString("submitted_by"), rs.getTimestamp("submitted_at").toInstant(),
                rs.getString("lesson_status")), taskId)
                .stream().findFirst().orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Pending review task not found"));
    }

    @Transactional(readOnly = true)
    public PageResult<ReviewQueueItem> reviewQueue(String query, int page, int size) {
        validatePage(query, page, size);
        String search = normalizeSearch(query);
        String pattern = search == null ? null : "%" + search + "%";
        long totalItems = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM content_review_tasks t
                JOIN lessons l ON l.id=t.lesson_id
                JOIN lesson_revisions r ON r.id=t.revision_id
                JOIN users u ON u.id=t.submitted_by
                WHERE t.status='PENDING'
                  AND (CAST(? AS text) IS NULL OR l.slug ILIKE ? OR r.title ILIKE ? OR u.name ILIKE ?)
                """, Long.class, search, pattern, pattern, pattern);
        long offset = (long) page * size;
        List<ReviewQueueItem> items = jdbc.query("""
                SELECT t.id AS task_id,l.id AS lesson_id,r.id AS revision_id,l.slug,r.title,
                       u.name AS submitted_by,t.status,t.submitted_at
                FROM content_review_tasks t
                JOIN lessons l ON l.id=t.lesson_id
                JOIN lesson_revisions r ON r.id=t.revision_id
                JOIN users u ON u.id=t.submitted_by
                WHERE t.status='PENDING'
                  AND (CAST(? AS text) IS NULL OR l.slug ILIKE ? OR r.title ILIKE ? OR u.name ILIKE ?)
                ORDER BY t.submitted_at ASC,t.id ASC
                LIMIT ? OFFSET ?
                """, (rs, row) -> new ReviewQueueItem(
                rs.getObject("task_id", UUID.class), rs.getObject("lesson_id", UUID.class),
                rs.getObject("revision_id", UUID.class), rs.getString("slug"), rs.getString("title"),
                rs.getString("submitted_by"), rs.getString("status"),
                rs.getTimestamp("submitted_at").toInstant()), search, pattern, pattern, pattern, size, offset);
        return new PageResult<>(items, page, size, totalItems, totalPages(totalItems, size));
    }

    @Transactional
    public Map<String, Object> decideReview(CurrentUser actor, UUID taskId, ReviewDecisionRequest request) {
        String decision = request.decision().trim().toUpperCase();
        if (!List.of("APPROVE", "REQUEST_CHANGES", "REJECT").contains(decision)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Decision must be APPROVE, REQUEST_CHANGES or REJECT");
        }
        var tasks = jdbc.query("""
                SELECT t.lesson_id,t.submitted_by,t.revision_id,l.status
                FROM content_review_tasks t JOIN lessons l ON l.id=t.lesson_id
                WHERE t.id=? AND t.status='PENDING'
                FOR UPDATE OF t,l
                """, (rs, row) -> new ReviewTaskOwner(
                        rs.getObject("lesson_id", UUID.class), rs.getObject("submitted_by", UUID.class),
                        rs.getObject("revision_id", UUID.class), rs.getString("status")), taskId);
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
        boolean publishedLesson = "PUBLISHED".equals(tasks.getFirst().lessonStatus());
        String lessonStatus = "APPROVE".equals(decision) ? "APPROVED" : "DRAFT";
        jdbc.update("""
                UPDATE content_review_tasks SET status=?,review_comment=?,reviewed_by=?,reviewed_at=?
                WHERE id=? AND status='PENDING'
                """, taskStatus, request.comment(), actor.id(), Timestamp.from(Instant.now()), taskId);
        if (publishedLesson) {
            if ("REJECT".equals(decision)) {
                jdbc.update("UPDATE lessons SET draft_revision_id=NULL,updated_at=now() WHERE id=? AND draft_revision_id=?",
                        lessonId, tasks.getFirst().revisionId());
            }
        } else {
            jdbc.update("UPDATE lessons SET status=?,updated_at=now() WHERE id=?", lessonStatus, lessonId);
        }
        audit(actor, "LESSON_REVIEW_" + decision, lessonId, Map.of("taskId", taskId.toString()));
        return Map.of("taskId", taskId, "lessonId", lessonId, "status", taskStatus);
    }

    @Transactional
    public Map<String, Object> publish(CurrentUser actor, UUID lessonId) {
        var states = jdbc.query("SELECT status,draft_revision_id FROM lessons WHERE id=? FOR UPDATE",
                (rs, row) -> new PublishState(rs.getString("status"),
                        rs.getObject("draft_revision_id", UUID.class)), lessonId);
        if (states.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found");
        }
        PublishState state = states.getFirst();
        UUID revisionId;
        if ("APPROVED".equals(state.status())) {
            revisionId = jdbc.queryForObject("""
                    SELECT revision_id FROM content_review_tasks
                    WHERE lesson_id=? AND status='APPROVED'
                    ORDER BY reviewed_at DESC LIMIT 1
                    """, UUID.class, lessonId);
        } else if ("PUBLISHED".equals(state.status()) && state.draftRevisionId() != null) {
            revisionId = state.draftRevisionId();
            Integer approved = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM content_review_tasks
                    WHERE lesson_id=? AND revision_id=? AND status='APPROVED'
                    """, Integer.class, lessonId, revisionId);
            if (approved == null || approved == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "The pending published revision has not been approved");
            }
        } else {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only an approved lesson or approved revision can be published");
        }
        int changed = jdbc.update("""
                UPDATE lessons SET status='PUBLISHED',published_revision_id=?,draft_revision_id=NULL,
                    content_revision=(SELECT revision_number FROM lesson_revisions WHERE id=?),updated_at=now()
                WHERE id=?
                """, revisionId, revisionId, lessonId);
        if (changed == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lesson publication did not complete");
        }
        audit(actor, "LESSON_PUBLISHED", lessonId, Map.of("revisionId", revisionId.toString()));
        return Map.of("lessonId", lessonId, "revisionId", revisionId, "status", "PUBLISHED");
    }

    private LessonDraftResponse updatePublishedDraft(CurrentUser actor, UUID lessonId, LessonDraftRequest request) {
        validateContent(request.contentJson());
        validateTaxonomy(request.difficulty(), request.sourceType());
        var current = jdbc.query("""
                SELECT l.slug,l.content_revision,l.draft_revision_id,c.slug AS category_slug
                FROM lessons l JOIN categories c ON c.id=l.category_id
                WHERE l.id=? AND l.status='PUBLISHED' FOR UPDATE OF l
                """, (rs, row) -> new PublishedLessonState(rs.getString("slug"), rs.getInt("content_revision"),
                rs.getObject("draft_revision_id", UUID.class), rs.getString("category_slug")), lessonId);
        if (current.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Published lesson not found");
        PublishedLessonState state = current.getFirst();
        if (!state.slug().equals(request.slug().trim())
                || !state.categorySlug().equals(request.categorySlug().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Slug and category cannot be changed in a published-lesson revision");
        }
        Integer pending = jdbc.queryForObject(
                "SELECT COUNT(*) FROM content_review_tasks WHERE lesson_id=? AND status='PENDING'",
                Integer.class, lessonId);
        if (pending != null && pending > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A lesson revision is already under review");
        }
        int nextRevision = jdbc.queryForObject(
                "SELECT COALESCE(MAX(revision_number),0)+1 FROM lesson_revisions WHERE lesson_id=?",
                Integer.class, lessonId);
        UUID revisionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO lesson_revisions(id,lesson_id,revision_number,title,summary,difficulty,
                    content_json,source_type,changed_by,change_note)
                VALUES (?,?,?,?,?,?,?::jsonb,?,?,?)
                """, revisionId, lessonId, nextRevision, request.title().trim(), request.summary(),
                request.difficulty().toUpperCase(), request.contentJson(), request.sourceType().toUpperCase(),
                actor.id(), request.changeNote());
        jdbc.update("""
                UPDATE lessons SET draft_revision_id=?,updated_at=now()
                WHERE id=? AND status='PUBLISHED'
                """, revisionId, lessonId);
        audit(actor, "LESSON_PUBLISHED_DRAFT_UPDATED", lessonId, Map.of("revision", nextRevision));
        return new LessonDraftResponse(lessonId, state.slug(), request.title().trim(), "DRAFT", nextRevision);
    }

    private record LessonReviewState(String status, int contentRevision, UUID draftRevisionId) {}
    private record ReviewTaskOwner(UUID lessonId, UUID submittedBy, UUID revisionId, String lessonStatus) {}
    private record PublishState(String status, UUID draftRevisionId) {}
    private record PublishedLessonState(String slug, int contentRevision, UUID draftRevisionId, String categorySlug) {}

    private static void validatePage(String query, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be between 1 and 100");
        }
        if (query != null && query.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query must not exceed 120 characters");
        }
    }

    private static String normalizeSearch(String query) {
        if (query == null || query.isBlank()) return null;
        return query.trim();
    }

    private static int totalPages(long totalItems, int size) {
        return totalItems == 0 ? 0 : (int) ((totalItems + size - 1) / size);
    }

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
