package com.jia.academy.analytics.service;

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

/**
 * Read-only, database-aggregated academy analytics. All academy-owned activity is
 * constrained by academy_id (directly or through the lesson taxonomy) in SQL.
 */
@Service
public class AcademyAnalyticsService {
    private final JdbcTemplate jdbc;

    public AcademyAnalyticsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummary summary(CurrentUser actor, int windowDays) {
        if (windowDays < 1 || windowDays > 365) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "windowDays must be between 1 and 365");
        }
        UUID academyId = primaryAcademyId(actor);
        Instant cutoff = Instant.now().minusSeconds(windowDays * 86_400L);

        SummaryRow row = jdbc.query("""
                SELECT a.id AS academy_id, a.code AS academy_code,
                    (SELECT COUNT(DISTINCT u.id)
                     FROM academy_memberships am
                     JOIN users u ON u.id=am.user_id
                     JOIN user_roles ur ON ur.user_id=u.id
                     JOIN roles r ON r.id=ur.role_id AND r.code='LEARNER'
                     WHERE am.academy_id=a.id AND am.status='ACTIVE' AND u.status='ACTIVE') AS total_learners,
                    (SELECT COUNT(DISTINCT activity.user_id) FROM (
                        SELECT lp.user_id
                        FROM learner_lesson_progress lp
                        JOIN lessons l ON l.id=lp.lesson_id
                        JOIN categories c ON c.id=l.category_id
                        JOIN technologies t ON t.id=c.technology_id
                        WHERE t.academy_id=a.id AND lp.updated_at>=?
                        UNION
                        SELECT qa.user_id FROM quiz_attempts qa
                        WHERE qa.academy_id=a.id AND qa.started_at>=?
                        UNION
                        SELECT rs.user_id FROM learner_revision_schedules rs
                        WHERE rs.academy_id=a.id AND rs.updated_at>=?
                    ) activity
                    JOIN academy_memberships am ON am.user_id=activity.user_id AND am.academy_id=a.id AND am.status='ACTIVE'
                    JOIN users u ON u.id=activity.user_id AND u.status='ACTIVE'
                    JOIN user_roles ur ON ur.user_id=u.id
                    JOIN roles r ON r.id=ur.role_id AND r.code='LEARNER') AS active_learners,
                    (SELECT COUNT(*)
                     FROM learner_lesson_progress lp
                     JOIN lessons l ON l.id=lp.lesson_id
                     JOIN categories c ON c.id=l.category_id
                     JOIN technologies t ON t.id=c.technology_id
                     JOIN academy_memberships am ON am.user_id=lp.user_id AND am.academy_id=a.id AND am.status='ACTIVE'
                     JOIN users u ON u.id=lp.user_id AND u.status='ACTIVE'
                     WHERE t.academy_id=a.id AND lp.status='COMPLETED'
                       AND EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id
                                   WHERE ur.user_id=u.id AND r.code='LEARNER')) AS lessons_completed,
                    (SELECT COUNT(*) FROM quiz_attempts qa
                     WHERE qa.academy_id=a.id AND qa.status='SUBMITTED' AND qa.submitted_at>=?) AS quiz_attempts,
                    (SELECT AVG(qa.score_percent) FROM quiz_attempts qa
                     WHERE qa.academy_id=a.id AND qa.status='SUBMITTED' AND qa.submitted_at>=?
                       AND qa.score_percent IS NOT NULL) AS average_score,
                    (SELECT COUNT(*) FROM quiz_attempts qa
                     WHERE qa.academy_id=a.id AND qa.status='SUBMITTED' AND qa.submitted_at>=?
                       AND qa.score_percent IS NOT NULL) AS scored_attempts
                FROM academies a WHERE a.id=? AND a.status='ACTIVE'
                """, (rs, n) -> new SummaryRow(
                    rs.getObject("academy_id", UUID.class), rs.getString("academy_code"),
                    rs.getLong("total_learners"), rs.getLong("active_learners"),
                    rs.getLong("lessons_completed"), rs.getLong("quiz_attempts"),
                    rs.getBigDecimal("average_score"), rs.getLong("scored_attempts")),
                Timestamp.from(cutoff), Timestamp.from(cutoff), Timestamp.from(cutoff),
                Timestamp.from(cutoff), Timestamp.from(cutoff), Timestamp.from(cutoff), academyId)
                .stream().findFirst().orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.FORBIDDEN, "Active academy membership is required"));

        List<TopicActivity> topics = jdbc.query("""
                SELECT t.slug AS topic_slug,t.name AS topic_name,COUNT(*) AS attempts,
                       AVG(qa.score_percent) AS average_score
                FROM quiz_attempts qa
                JOIN technologies t ON t.id=qa.technology_id AND t.academy_id=qa.academy_id
                WHERE qa.academy_id=? AND qa.status='SUBMITTED' AND qa.submitted_at>=?
                GROUP BY t.slug,t.name
                ORDER BY attempts DESC,t.name ASC
                LIMIT 5
                """, (rs, n) -> new TopicActivity(rs.getString("topic_slug"), rs.getString("topic_name"),
                rs.getLong("attempts"), rs.getBigDecimal("average_score")), academyId, Timestamp.from(cutoff));

        return new AnalyticsSummary(row.academyId(), row.academyCode(), windowDays,
                row.totalLearners(), row.activeLearners(), row.lessonsCompleted(),
                row.quizAttempts(), row.averageScore(), row.scoredAttempts(), topics);
    }

    private UUID primaryAcademyId(CurrentUser actor) {
        if (actor == null || actor.id() == null || !actor.permissions().contains("users.manage")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Academy administrator permission is required");
        }
        return jdbc.query("""
                SELECT am.academy_id FROM academy_memberships am
                JOIN academies a ON a.id=am.academy_id
                WHERE am.user_id=? AND am.status='ACTIVE' AND am.is_default=TRUE AND a.status='ACTIVE'
                """, (rs, n) -> rs.getObject("academy_id", UUID.class), actor.id())
                .stream().findFirst().orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.FORBIDDEN, "An active default academy membership is required"));
    }

    private record SummaryRow(UUID academyId, String academyCode, long totalLearners,
            long activeLearners, long lessonsCompleted, long quizAttempts,
            BigDecimal averageScore, long scoredAttempts) {}

    public record AnalyticsSummary(UUID academyId, String academyCode, int windowDays,
            long totalLearners, long activeLearners, long lessonsCompleted,
            long quizAttempts, BigDecimal averageScorePercent, long scoredAttempts,
            List<TopicActivity> topTopics) {}

    public record TopicActivity(String topicSlug, String topicName, long attempts,
            BigDecimal averageScorePercent) {}
}
