package com.jia.academy.content.service;

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
    private final LessonContentValidator contentValidator;

    public ContentAuthoringService(JdbcTemplate jdbc, ObjectMapper mapper, LessonContentValidator contentValidator) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.contentValidator = contentValidator;
    }

    @Transactional
    public LessonDraftResponse createDraft(CurrentUser actor, LessonDraftRequest request) {
        validateContent(request.contentJson());
        validateTaxonomy(request.difficulty(), request.sourceType());
        UUID categoryId = jdbc.query(
                "SELECT c.id FROM categories c JOIN technologies t ON t.id=c.technology_id WHERE c.slug=? AND t.academy_id=?",
                (rs, row) -> rs.getObject("id", UUID.class), request.categorySlug(), primaryAcademyId(actor))
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
        // Serialize edits to the same lesson so concurrent editors cannot
        // allocate the same next revision number or overwrite each other's draft.
        String currentStatus = jdbc.query(
                "SELECT l.status FROM lessons l JOIN categories c ON c.id=l.category_id JOIN technologies t ON t.id=c.technology_id WHERE l.id=? AND t.academy_id=? FOR UPDATE OF l",
                (rs, row) -> rs.getString(1), lessonId, primaryAcademyId(actor)).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found"));
        if ("PUBLISHED".equals(currentStatus)) {
            return updatePublishedDraft(actor, lessonId, request);
        }
        validateContent(request.contentJson());
        validateTaxonomy(request.difficulty(), request.sourceType());
        Integer currentRevision = jdbc.query(
                "SELECT l.content_revision FROM lessons l JOIN categories c ON c.id=l.category_id JOIN technologies t ON t.id=c.technology_id WHERE l.id=? AND l.status='DRAFT' AND t.academy_id=?",
                (rs, row) -> rs.getInt(1), lessonId, primaryAcademyId(actor)).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Only draft lessons can be edited in this workflow"));
        UUID categoryId = jdbc.query(
                "SELECT c.id FROM categories c JOIN technologies t ON t.id=c.technology_id WHERE c.slug=? AND t.academy_id=?",
                (rs, row) -> rs.getObject("id", UUID.class), request.categorySlug(), primaryAcademyId(actor))
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
                SELECT l.status, l.content_revision, l.draft_revision_id
                FROM lessons l JOIN categories c ON c.id=l.category_id JOIN technologies t ON t.id=c.technology_id
                WHERE l.id=? AND t.academy_id=? FOR UPDATE OF l
                """, (rs, row) -> new LessonReviewState(rs.getString("status"),
                        rs.getInt("content_revision"), rs.getObject("draft_revision_id", UUID.class)), lessonId, primaryAcademyId(actor));
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
    public PageResult<LessonAdminListItem> adminLessons(CurrentUser actor, String status, String query, int page, int size) {
        validatePage(query, page, size);
        if (status != null && !List.of("DRAFT", "IN_REVIEW", "APPROVED", "PUBLISHED", "ARCHIVED").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported lesson status filter");
        }
        String search = normalizeSearch(query);
        String pattern = search == null ? null : search + "%";
        long totalItems = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM lessons l JOIN categories c ON c.id=l.category_id JOIN technologies tech ON tech.id=c.technology_id
                WHERE tech.academy_id=? AND (CAST(? AS text) IS NULL OR l.status=?)
                  AND (CAST(? AS text) IS NULL OR lower(l.title) LIKE lower(?) OR lower(l.slug) LIKE lower(?) OR lower(c.slug) LIKE lower(?))
                """, Long.class, primaryAcademyId(actor), status, status, search, pattern, pattern, pattern);
        long offset = (long) page * size;
        List<LessonAdminListItem> items = jdbc.query("""
                SELECT l.id,l.slug,l.title,c.slug AS category_slug,l.status,l.content_revision,
                       (SELECT revision_number FROM lesson_revisions WHERE id=l.published_revision_id) AS published_revision,
                       (SELECT revision_number FROM lesson_revisions WHERE id=l.draft_revision_id) AS draft_revision,
                       l.updated_at
                FROM lessons l JOIN categories c ON c.id=l.category_id JOIN technologies tech ON tech.id=c.technology_id
                WHERE tech.academy_id=? AND (CAST(? AS text) IS NULL OR l.status=?)
                  AND (CAST(? AS text) IS NULL OR lower(l.title) LIKE lower(?) OR lower(l.slug) LIKE lower(?) OR lower(c.slug) LIKE lower(?))
                ORDER BY l.updated_at DESC,l.id DESC
                LIMIT ? OFFSET ?
                """, (rs, row) -> new LessonAdminListItem(
                rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("title"),
                rs.getString("category_slug"), rs.getString("status"), rs.getInt("content_revision"),
                (Integer) rs.getObject("published_revision"), (Integer) rs.getObject("draft_revision"),
                rs.getTimestamp("updated_at").toInstant()),
                primaryAcademyId(actor), status, status, search, pattern, pattern, pattern, size, offset);
        return new PageResult<>(items, page, size, totalItems, totalPages(totalItems, size));
    }

    @Transactional(readOnly = true)
    public LessonPreviewResponse preview(CurrentUser actor, UUID lessonId) {
        return jdbc.query("""
                SELECT l.id,l.slug,r.title,r.summary,r.difficulty,r.revision_number,
                       r.source_type,r.content_json::text AS content_json,l.status
                FROM lessons l
                JOIN categories c ON c.id=l.category_id
                JOIN technologies tech ON tech.id=c.technology_id
                JOIN lesson_revisions r ON r.id=COALESCE(
                    l.draft_revision_id,
                    CASE WHEN l.status IN ('PUBLISHED','ARCHIVED') THEN l.published_revision_id
                         ELSE (SELECT revision.id FROM lesson_revisions revision
                               WHERE revision.lesson_id=l.id
                                 AND revision.revision_number=l.content_revision
                               LIMIT 1)
                    END)
                WHERE l.id=? AND tech.academy_id=?
                """, (rs, row) -> new LessonPreviewResponse(
                rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("title"),
                rs.getString("summary"), rs.getString("difficulty"), rs.getInt("revision_number"),
                rs.getString("source_type"), rs.getString("content_json"), true, rs.getString("status")),
                lessonId, primaryAcademyId(actor)).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found"));
    }

    @Transactional(readOnly = true)
    public LessonAuthoringDetail authoringDetail(CurrentUser actor, UUID lessonId) {
        return jdbc.query("""
                SELECT l.id,l.slug,l.status,l.updated_at,c.slug AS category_slug,
                       r.title,r.summary,r.difficulty,r.source_type,r.content_json::text,
                       r.revision_number,r.change_note,
                       (SELECT revision_number FROM lesson_revisions WHERE id=l.published_revision_id) AS published_revision,
                       (l.status <> 'PUBLISHED' OR l.draft_revision_id IS NOT NULL) AS editing_draft
                FROM lessons l
                JOIN categories c ON c.id=l.category_id
                JOIN technologies tech ON tech.id=c.technology_id
                JOIN lesson_revisions r ON r.id=CASE
                    WHEN l.status='PUBLISHED' AND l.draft_revision_id IS NOT NULL THEN l.draft_revision_id
                    WHEN l.status='PUBLISHED' THEN l.published_revision_id
                    ELSE (SELECT id FROM lesson_revisions
                          WHERE lesson_id=l.id AND revision_number=l.content_revision)
                END
                WHERE l.id=? AND tech.academy_id=?
                """, (rs, row) -> new LessonAuthoringDetail(
                rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("title"),
                rs.getString("summary"), rs.getString("category_slug"), rs.getString("difficulty"),
                rs.getString("source_type"), rs.getString("content_json"), rs.getString("status"),
                rs.getInt("revision_number"), (Integer) rs.getObject("published_revision"),
                rs.getBoolean("editing_draft"), rs.getString("change_note"),
                rs.getTimestamp("updated_at").toInstant()), lessonId, primaryAcademyId(actor))
                .stream().findFirst().orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found"));
    }

    @Transactional(readOnly = true)
    public ReviewDetail reviewDetail(CurrentUser actor, UUID taskId) {
        return jdbc.query("""
                SELECT t.id AS task_id,l.id AS lesson_id,r.id AS revision_id,l.slug,
                       r.title,r.summary,r.difficulty,r.source_type,r.content_json::text,
                       r.revision_number,r.change_note,u.name AS submitted_by,
                       t.submitted_at,l.status AS lesson_status
                FROM content_review_tasks t
                JOIN lessons l ON l.id=t.lesson_id
                JOIN categories c ON c.id=l.category_id
                JOIN technologies tech ON tech.id=c.technology_id
                JOIN lesson_revisions r ON r.id=t.revision_id
                JOIN users u ON u.id=t.submitted_by
                WHERE t.id=? AND t.status='PENDING' AND tech.academy_id=?
                """, (rs, row) -> new ReviewDetail(
                rs.getObject("task_id", UUID.class), rs.getObject("lesson_id", UUID.class),
                rs.getObject("revision_id", UUID.class), rs.getString("slug"), rs.getString("title"),
                rs.getString("summary"), rs.getString("difficulty"), rs.getString("source_type"),
                rs.getString("content_json"), rs.getInt("revision_number"), rs.getString("change_note"),
                rs.getString("submitted_by"), rs.getTimestamp("submitted_at").toInstant(),
                rs.getString("lesson_status")), taskId, primaryAcademyId(actor))
                .stream().findFirst().orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Pending review task not found"));
    }

    @Transactional(readOnly = true)
    public PageResult<ReviewQueueItem> reviewQueue(CurrentUser actor, String query, int page, int size) {
        validatePage(query, page, size);
        String search = normalizeSearch(query);
        String pattern = search == null ? null : search + "%";
        long totalItems = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM content_review_tasks t
                JOIN lessons l ON l.id=t.lesson_id
                JOIN categories c ON c.id=l.category_id
                JOIN technologies tech ON tech.id=c.technology_id
                JOIN lesson_revisions r ON r.id=t.revision_id
                JOIN users u ON u.id=t.submitted_by
                WHERE tech.academy_id=? AND t.status='PENDING'
                  AND (CAST(? AS text) IS NULL OR lower(l.slug) LIKE lower(?) OR lower(r.title) LIKE lower(?) OR lower(u.name) LIKE lower(?))
                """, Long.class, primaryAcademyId(actor), search, pattern, pattern, pattern);
        long offset = (long) page * size;
        List<ReviewQueueItem> items = jdbc.query("""
                SELECT t.id AS task_id,l.id AS lesson_id,r.id AS revision_id,l.slug,r.title,
                       u.name AS submitted_by,t.status,t.submitted_at
                FROM content_review_tasks t
                JOIN lessons l ON l.id=t.lesson_id
                JOIN categories c ON c.id=l.category_id
                JOIN technologies tech ON tech.id=c.technology_id
                JOIN lesson_revisions r ON r.id=t.revision_id
                JOIN users u ON u.id=t.submitted_by
                WHERE tech.academy_id=? AND t.status='PENDING'
                  AND (CAST(? AS text) IS NULL OR lower(l.slug) LIKE lower(?) OR lower(r.title) LIKE lower(?) OR lower(u.name) LIKE lower(?))
                ORDER BY t.submitted_at ASC,t.id ASC
                LIMIT ? OFFSET ?
                """, (rs, row) -> new ReviewQueueItem(
                rs.getObject("task_id", UUID.class), rs.getObject("lesson_id", UUID.class),
                rs.getObject("revision_id", UUID.class), rs.getString("slug"), rs.getString("title"),
                rs.getString("submitted_by"), rs.getString("status"),
                rs.getTimestamp("submitted_at").toInstant()), primaryAcademyId(actor), search, pattern, pattern, pattern, size, offset);
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
                WHERE t.id=? AND t.status='PENDING' AND t.lesson_id IN (SELECT l2.id FROM lessons l2 JOIN categories c2 ON c2.id=l2.category_id JOIN technologies tech2 ON tech2.id=c2.technology_id WHERE tech2.academy_id=?)
                FOR UPDATE OF t,l
                """, (rs, row) -> new ReviewTaskOwner(
                        rs.getObject("lesson_id", UUID.class), rs.getObject("submitted_by", UUID.class),
                        rs.getObject("revision_id", UUID.class), rs.getString("status")), taskId, primaryAcademyId(actor));
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
        var states = jdbc.query("SELECT l.status,l.draft_revision_id FROM lessons l JOIN categories c ON c.id=l.category_id JOIN technologies tech ON tech.id=c.technology_id WHERE l.id=? AND tech.academy_id=? FOR UPDATE OF l",
                (rs, row) -> new PublishState(rs.getString("status"),
                        rs.getObject("draft_revision_id", UUID.class)), lessonId, primaryAcademyId(actor));
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
        // Fail closed: validate the exact immutable snapshot and approval being published.
        var snapshotRows = jdbc.query("""
                SELECT revision_number,source_type,content_json::text AS content_json
                FROM lesson_revisions WHERE id=? AND lesson_id=?
                """, (rs, row) -> new ApprovedRevision(rs.getInt("revision_number"),
                rs.getString("source_type"), rs.getString("content_json")), revisionId, lessonId);
        if (snapshotRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Approved revision snapshot is missing");
        }
        ApprovedRevision snapshot = snapshotRows.getFirst();
        contentValidator.validate(snapshot.contentJson());
        validatePublicationEditorialGate(snapshot.contentJson(), snapshot.sourceType());
        Integer exactApproval = jdbc.queryForObject("""
                SELECT COUNT(*) FROM content_review_tasks
                WHERE lesson_id=? AND revision_id=? AND status='APPROVED'
                """, Integer.class, lessonId, revisionId);
        if (exactApproval == null || exactApproval == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Publication requires approval for this exact revision");
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

    @Transactional
    public Map<String, Object> unpublish(CurrentUser actor, UUID lessonId) {
        var states = jdbc.query("""
                SELECT l.status, l.published_revision_id, l.draft_revision_id
                FROM lessons l
                JOIN categories c ON c.id=l.category_id
                JOIN technologies tech ON tech.id=c.technology_id
                WHERE l.id=? AND tech.academy_id=?
                FOR UPDATE OF l
                """, (rs, row) -> new UnpublishState(rs.getString("status"),
                rs.getObject("published_revision_id", UUID.class),
                rs.getObject("draft_revision_id", UUID.class)), lessonId, primaryAcademyId(actor));
        if (states.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found");
        }
        UnpublishState state = states.getFirst();
        if (!"PUBLISHED".equals(state.status()) || state.publishedRevisionId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a published lesson can be unpublished");
        }
        if (state.draftRevisionId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Resolve the pending draft revision before unpublishing this lesson");
        }
        Integer pendingReviews = jdbc.queryForObject("""
                SELECT COUNT(*) FROM content_review_tasks
                WHERE lesson_id=? AND status='PENDING'
                """, Integer.class, lessonId);
        if (pendingReviews != null && pendingReviews > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Resolve pending review tasks before unpublishing this lesson");
        }
        int changed = jdbc.update("""
                UPDATE lessons SET status='DRAFT',updated_at=now()
                WHERE id=? AND status='PUBLISHED' AND draft_revision_id IS NULL
                """, lessonId);
        if (changed != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lesson unpublish did not complete");
        }
        audit(actor, "LESSON_UNPUBLISHED", lessonId,
                Map.of("publishedRevisionId", state.publishedRevisionId().toString()));
        return Map.of("lessonId", lessonId, "publishedRevisionId", state.publishedRevisionId(),
                "status", "DRAFT");
    }

    @Transactional
    public Map<String, Object> archive(CurrentUser actor, UUID lessonId) {
        var states = jdbc.query("""
                SELECT l.status
                FROM lessons l
                JOIN categories c ON c.id=l.category_id
                JOIN technologies tech ON tech.id=c.technology_id
                WHERE l.id=? AND tech.academy_id=?
                FOR UPDATE OF l
                """, (rs, row) -> rs.getString("status"), lessonId, primaryAcademyId(actor));
        if (states.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found");
        }
        String status = states.getFirst();
        if ("ARCHIVED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lesson is already archived");
        }
        if ("IN_REVIEW".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A lesson under review cannot be archived; resolve the review task first");
        }
        Integer pendingReviews = jdbc.queryForObject("""
                SELECT COUNT(*) FROM content_review_tasks
                WHERE lesson_id=? AND status='PENDING'
                """, Integer.class, lessonId);
        if (pendingReviews != null && pendingReviews > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Resolve pending review tasks before archiving this lesson");
        }
        int changed = jdbc.update("""
                UPDATE lessons SET status='ARCHIVED',updated_at=now()
                WHERE id=? AND status=? 
                """, lessonId, status);
        if (changed != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lesson archive did not complete");
        }
        audit(actor, "LESSON_ARCHIVED", lessonId, Map.of("previousStatus", status));
        return Map.of("lessonId", lessonId, "previousStatus", status, "status", "ARCHIVED");
    }

    @Transactional
    public LessonDraftResponse rollback(CurrentUser actor, UUID lessonId, RollbackRequest request) {
        var states = jdbc.query("""
                SELECT l.status, l.content_revision, l.published_revision_id, l.draft_revision_id
                FROM lessons l JOIN categories c ON c.id=l.category_id JOIN technologies tech ON tech.id=c.technology_id
                WHERE l.id=? AND tech.academy_id=? FOR UPDATE OF l
                """, (rs, row) -> new RollbackLessonState(
                rs.getString("status"), rs.getInt("content_revision"),
                rs.getObject("published_revision_id", UUID.class),
                rs.getObject("draft_revision_id", UUID.class)), lessonId, primaryAcademyId(actor));
        if (states.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found");
        }
        RollbackLessonState state = states.getFirst();
        if (!List.of("DRAFT", "PUBLISHED").contains(state.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only draft or published lessons can be rolled back");
        }
        if (state.draftRevisionId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Resolve or discard the existing draft revision before rolling back");
        }
        if ("DRAFT".equals(state.status()) && request.revisionNumber().equals(state.contentRevision())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The selected revision is already the current draft");
        }
        var targets = jdbc.query("""
                SELECT title, summary, difficulty, content_json::text AS content_json, source_type
                FROM lesson_revisions
                WHERE lesson_id=? AND revision_number=?
                """, (rs, row) -> new RollbackRevision(
                rs.getString("title"), rs.getString("summary"), rs.getString("difficulty"),
                rs.getString("content_json"), rs.getString("source_type")),
                lessonId, request.revisionNumber());
        if (targets.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Requested lesson revision not found");
        }
        RollbackRevision target = targets.getFirst();
        int nextRevision = jdbc.queryForObject(
                "SELECT COALESCE(MAX(revision_number),0)+1 FROM lesson_revisions WHERE lesson_id=?",
                Integer.class, lessonId);
        UUID newRevisionId = UUID.randomUUID();
        String note = "Rollback to revision " + request.revisionNumber()
                + (request.changeNote() == null || request.changeNote().isBlank()
                ? "" : ": " + request.changeNote().trim());
        jdbc.update("""
                INSERT INTO lesson_revisions(id,lesson_id,revision_number,title,summary,difficulty,
                    content_json,source_type,changed_by,change_note)
                VALUES (?,?,?,?,?,?,?::jsonb,?,?,?)
                """, newRevisionId, lessonId, nextRevision, target.title(), target.summary(),
                target.difficulty(), target.contentJson(), target.sourceType(), actor.id(), note);
        if ("PUBLISHED".equals(state.status())) {
            jdbc.update("""
                    UPDATE lessons SET draft_revision_id=?,updated_at=now()
                    WHERE id=? AND status='PUBLISHED'
                    """, newRevisionId, lessonId);
        } else {
            jdbc.update("""
                    UPDATE lessons SET title=?,summary=?,difficulty=?,content_json=?::jsonb,
                        source_type=?,content_revision=?,status='DRAFT',updated_at=now()
                    WHERE id=? AND status='DRAFT'
                    """, target.title(), target.summary(), target.difficulty(), target.contentJson(),
                    target.sourceType(), nextRevision, lessonId);
        }
        audit(actor, "LESSON_REVISION_ROLLED_BACK", lessonId,
                Map.of("sourceRevision", request.revisionNumber(), "createdRevision", nextRevision));
        return new LessonDraftResponse(lessonId,
                jdbc.queryForObject("SELECT slug FROM lessons WHERE id=?", String.class, lessonId),
                target.title(), "DRAFT", nextRevision);
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

    private record RollbackLessonState(String status, int contentRevision, UUID publishedRevisionId, UUID draftRevisionId) {}
    private record RollbackRevision(String title, String summary, String difficulty, String contentJson, String sourceType) {}
    private record LessonReviewState(String status, int contentRevision, UUID draftRevisionId) {}
    private record ReviewTaskOwner(UUID lessonId, UUID submittedBy, UUID revisionId, String lessonStatus) {}
    private record PublishState(String status, UUID draftRevisionId) {}
    private record ApprovedRevision(int revisionNumber, String sourceType, String contentJson) {}

    private record UnpublishState(String status, UUID publishedRevisionId, UUID draftRevisionId) {}
    private record PublishedLessonState(String slug, int contentRevision, UUID draftRevisionId, String categorySlug) {}


    private static UUID primaryAcademyId(CurrentUser actor) {
        if (actor == null || actor.academyIds() == null || actor.academyIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "An active academy membership is required");
        }
        return actor.academyIds().getFirst();
    }

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

    private void validatePublicationEditorialGate(String contentJson, String sourceType) {
        try {
            var root = mapper.readTree(contentJson);
            var blocks = root.path("blocks");
            boolean explanation = false, practice = false;
            for (var block : blocks) {
                String type = block.path("type").asText("");
                explanation |= type.equals("markdown") || type.equals("interviewAnswer");
                practice |= type.equals("practice") || type.equals("question")
                        || type.equals("quiz") || type.equals("exercise");
            }
            if (!explanation) throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Editorial gate: add an explanation or structured interview answer");
            if (!practice) throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Editorial gate: add practice, a question, quiz or exercise");
            if ("EXTERNAL_REFERENCE".equalsIgnoreCase(sourceType)) {
                var sources = root.path("sources");
                if (!sources.isArray() || sources.isEmpty())
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "External content requires sources[] provenance");
                for (var source : sources) {
                    if (source.path("url").asText("").isBlank()
                            || source.path("title").asText("").isBlank()
                            || source.path("accessedAt").asText("").isBlank()
                            || source.path("licenseNotes").asText("").isBlank())
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Each source needs url, title, accessedAt and licenseNotes");
                }
            }
        } catch (ResponseStatusException ex) { throw ex; }
        catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Editorial metadata is invalid");
        }
    }

    private void validateContent(String contentJson) {
        contentValidator.validate(contentJson);
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
