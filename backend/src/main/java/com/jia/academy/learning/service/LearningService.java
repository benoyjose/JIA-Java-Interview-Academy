package com.jia.academy.learning.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jia.academy.security.CurrentUser;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LearningService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public LearningService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<ProgressItem> progress(CurrentUser user) {
        return jdbc.query("""
                SELECT l.slug,r.content_revision,p.status,p.progress_percent,p.last_position_json::text,
                       p.updated_at
                FROM learner_lesson_progress p JOIN lessons l ON l.id=p.lesson_id
                LEFT JOIN LATERAL (SELECT revision_number AS content_revision FROM lesson_revisions
                    WHERE id=l.published_revision_id) r ON TRUE
                WHERE p.user_id=? ORDER BY p.updated_at DESC LIMIT 200
                """, (rs, row) -> new ProgressItem(rs.getString("slug"),
                rs.getInt("content_revision"), rs.getString("status"),
                rs.getBigDecimal("progress_percent"), parsePosition(rs.getString("last_position_json")),
                rs.getTimestamp("updated_at").toInstant()), user.id());
    }

    @Transactional
    public ProgressItem updateProgress(CurrentUser user, String lessonSlug, ProgressRequest request) {
        if (request.contentRevision() == null || request.contentRevision() < 1
                || request.progressPercent() == null
                || request.progressPercent().compareTo(BigDecimal.ZERO) < 0
                || request.progressPercent().compareTo(BigDecimal.valueOf(100)) > 0
                || (request.lastPosition() != null && !request.lastPosition().isObject())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid progress payload");
        }
        var lessons = jdbc.query("""
                SELECT l.id,r.revision_number
                FROM lessons l JOIN lesson_revisions r ON r.id=l.published_revision_id
                WHERE l.slug=? AND l.status='PUBLISHED'
                """, (rs, row) -> new PublishedLesson(rs.getObject("id", UUID.class), rs.getInt("revision_number")),
                lessonSlug);
        if (lessons.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Published lesson not found");
        PublishedLesson lesson = lessons.getFirst();
        if (request.contentRevision() > lesson.revision()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Progress refers to a lesson revision newer than the current published revision");
        }
        String position = null;
        try {
            if (request.lastPosition() != null) position = mapper.writeValueAsString(request.lastPosition());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid lastPosition payload");
        }
        jdbc.update("""
                INSERT INTO learner_lesson_progress(user_id,lesson_id,content_revision,status,progress_percent,
                    last_position_json,started_at,completed_at,updated_at)
                VALUES (?,?,? ,CASE WHEN ? >= 100 THEN 'COMPLETED' ELSE 'IN_PROGRESS' END,? ,?::jsonb,
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
                """, user.id(), lesson.id(), lesson.revision(), request.progressPercent(),
                request.progressPercent(), position, request.progressPercent());
        return jdbc.query("""
                SELECT l.slug,p.content_revision,p.status,p.progress_percent,p.last_position_json::text,p.updated_at
                FROM learner_lesson_progress p JOIN lessons l ON l.id=p.lesson_id
                WHERE p.user_id=? AND p.lesson_id=?
                """, (rs, row) -> new ProgressItem(rs.getString("slug"), rs.getInt("content_revision"),
                rs.getString("status"), rs.getBigDecimal("progress_percent"),
                parsePosition(rs.getString("last_position_json")), rs.getTimestamp("updated_at").toInstant()),
                user.id(), lesson.id()).getFirst();
    }

    @Transactional(readOnly = true)
    public List<BookmarkItem> bookmarks(CurrentUser user) {
        return jdbc.query("""
                SELECT l.slug,l.title,b.created_at
                FROM learner_bookmarks b JOIN lessons l ON l.id=b.lesson_id
                WHERE b.user_id=? ORDER BY b.created_at DESC LIMIT 200
                """, (rs, row) -> new BookmarkItem(rs.getString("slug"), rs.getString("title"),
                rs.getTimestamp("created_at").toInstant()), user.id());
    }

    @Transactional
    public void bookmark(CurrentUser user, String lessonSlug) {
        UUID lessonId = publishedLessonId(lessonSlug);
        jdbc.update("""
                INSERT INTO learner_bookmarks(user_id,lesson_id,created_at)
                VALUES (?,?,now()) ON CONFLICT (user_id,lesson_id) DO NOTHING
                """, user.id(), lessonId);
    }

    @Transactional
    public void removeBookmark(CurrentUser user, String lessonSlug) {
        jdbc.update("""
                DELETE FROM learner_bookmarks b USING lessons l
                WHERE b.lesson_id=l.id AND b.user_id=? AND l.slug=?
                """, user.id(), lessonSlug);
    }

    @Transactional(readOnly = true)
    public SyncDiagnostics diagnostics(CurrentUser user) {
        Integer progressCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM learner_lesson_progress WHERE user_id=?", Integer.class, user.id());
        Integer bookmarkCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM learner_bookmarks WHERE user_id=?", Integer.class, user.id());
        Timestamp latest = jdbc.query("""
                SELECT MAX(updated_at) FROM learner_lesson_progress WHERE user_id=?
                """, rs -> rs.next() ? rs.getTimestamp(1) : null, user.id());
        return new SyncDiagnostics(progressCount == null ? 0 : progressCount,
                bookmarkCount == null ? 0 : bookmarkCount,
                latest == null ? null : latest.toInstant(), Instant.now());
    }

    private JsonNode parsePosition(String value) {
        if (value == null) return null;
        try { return mapper.readTree(value); }
        catch (Exception ex) { throw new IllegalStateException("Stored progress position is invalid JSON", ex); }
    }

    private UUID publishedLessonId(String slug) {
        return jdbc.query("""
                SELECT id FROM lessons WHERE slug=? AND status='PUBLISHED'
                """, (rs, row) -> rs.getObject(1, UUID.class), slug).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Published lesson not found"));
    }

    public record ProgressRequest(Integer contentRevision, BigDecimal progressPercent, JsonNode lastPosition) {}
    public record ProgressItem(String lessonSlug, int contentRevision, String status, BigDecimal progressPercent,
                               JsonNode lastPosition, Instant updatedAt) {}
    public record BookmarkItem(String lessonSlug, String title, Instant createdAt) {}
    public record SyncDiagnostics(int progressItems, int bookmarkItems, Instant latestProgressAt, Instant serverTime) {}
    private record PublishedLesson(UUID id, int revision) {}
}
