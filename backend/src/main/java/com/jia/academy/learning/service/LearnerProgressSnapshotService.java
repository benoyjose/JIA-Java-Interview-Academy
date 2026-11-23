package com.jia.academy.learning.service;

import com.jia.academy.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Server-derived learner snapshot; never accepts client mastery or score values. */
@Service
public class LearnerProgressSnapshotService {
    private final JdbcTemplate jdbc;
    public LearnerProgressSnapshotService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public Snapshot load(CurrentUser user, int requestedLimit, String afterSlug) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        UUID academy = user.academyIds() == null || user.academyIds().isEmpty()
                ? null : user.academyIds().getFirst();
        if (academy == null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Academy membership is required");
        var rows = jdbc.query("""
                SELECT l.slug,l.title,p.content_revision,p.status,p.progress_percent,
                       p.last_position_json,p.updated_at
                FROM learner_lesson_progress p
                JOIN lessons l ON l.id=p.lesson_id
                JOIN categories c ON c.id=l.category_id
                JOIN technologies t ON t.id=c.technology_id
                WHERE p.user_id=? AND t.academy_id=?
                  AND (? IS NULL OR l.slug > ?)
                ORDER BY l.slug ASC LIMIT ?
                """, (rs, n) -> new LessonProgress(rs.getString("slug"),rs.getString("title"),
                rs.getInt("content_revision"),rs.getString("status"),rs.getBigDecimal("progress_percent"),
                rs.getString("last_position_json"),rs.getTimestamp("updated_at").toInstant()),
                user.id(),academy,afterSlug,afterSlug,limit+1);
        boolean hasMore=rows.size()>limit;
        List<LessonProgress> page=hasMore?List.copyOf(rows.subList(0,limit)):List.copyOf(rows);
        var totals=jdbc.query("""
                SELECT COUNT(*) total,COUNT(*) FILTER (WHERE status='COMPLETED') completed,
                       COALESCE(AVG(progress_percent),0) average
                FROM learner_lesson_progress p JOIN lessons l ON l.id=p.lesson_id
                JOIN categories c ON c.id=l.category_id JOIN technologies t ON t.id=c.technology_id
                WHERE p.user_id=? AND t.academy_id=?
                """,(rs,n)->new Aggregate(rs.getLong("total"),rs.getLong("completed"),rs.getBigDecimal("average")),
                user.id(),academy).getFirst();
        return new Snapshot(page,hasMore?page.getLast().lessonSlug():null,totals);
    }
    public record LessonProgress(String lessonSlug,String title,int contentRevision,String status,
            java.math.BigDecimal progressPercent,String lastPositionJson,java.time.Instant updatedAt){}
    public record Aggregate(long lessonsStarted,long lessonsCompleted,java.math.BigDecimal averageProgressPercent){}
    public record Snapshot(List<LessonProgress> items,String nextAfterSlug,Aggregate aggregate){}
}
