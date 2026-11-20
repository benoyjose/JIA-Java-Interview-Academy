package com.jia.academy.content.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LessonRevisionHistoryService {
    private final JdbcTemplate jdbc;

    public LessonRevisionHistoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<RevisionHistoryItem> history(UUID lessonId) {
        Boolean exists = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM lessons WHERE id=?)",
                Boolean.class, lessonId);
        if (!Boolean.TRUE.equals(exists)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found");
        }
        return jdbc.query("""
                SELECT r.id,r.revision_number,r.title,r.summary,r.difficulty,r.source_type,
                       r.changed_by,COALESCE(u.name,'System') AS changed_by_name,r.change_note,r.created_at,
                       CASE WHEN l.published_revision_id=r.id THEN TRUE ELSE FALSE END AS is_published,
                       CASE WHEN l.draft_revision_id=r.id THEN TRUE ELSE FALSE END AS is_current_draft,
                       latest.status AS review_status,latest.review_comment,latest.reviewed_at,
                       submitter.name AS submitted_by
                FROM lesson_revisions r
                JOIN lessons l ON l.id=r.lesson_id
                LEFT JOIN users u ON u.id=r.changed_by
                LEFT JOIN LATERAL (
                    SELECT t.status,t.review_comment,t.reviewed_at,t.submitted_by
                    FROM content_review_tasks t
                    WHERE t.revision_id=r.id
                    ORDER BY t.submitted_at DESC,t.id DESC LIMIT 1
                ) latest ON TRUE
                LEFT JOIN users submitter ON submitter.id=latest.submitted_by
                WHERE r.lesson_id=?
                ORDER BY r.revision_number DESC
                """, (rs, row) -> new RevisionHistoryItem(
                rs.getObject("id", UUID.class), rs.getInt("revision_number"), rs.getString("title"),
                rs.getString("summary"), rs.getString("difficulty"), rs.getString("source_type"),
                rs.getObject("changed_by", UUID.class), rs.getString("changed_by_name"),
                rs.getString("change_note"), rs.getTimestamp("created_at").toInstant(),
                rs.getBoolean("is_published"), rs.getBoolean("is_current_draft"),
                rs.getString("review_status"), rs.getString("review_comment"),
                rs.getTimestamp("reviewed_at") == null ? null : rs.getTimestamp("reviewed_at").toInstant(),
                rs.getString("submitted_by")), lessonId);
    }

    public record RevisionHistoryItem(UUID revisionId, int revisionNumber, String title, String summary,
            String difficulty, String sourceType, UUID changedBy, String changedByName, String changeNote,
            Instant createdAt, boolean published, boolean currentDraft, String reviewStatus,
            String reviewComment, Instant reviewedAt, String submittedBy) {}
}
