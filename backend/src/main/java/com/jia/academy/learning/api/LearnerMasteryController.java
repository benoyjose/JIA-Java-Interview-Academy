package com.jia.academy.learning.api;

import com.jia.academy.appregistry.api.AcademyAppContextInterceptor;
import com.jia.academy.appregistry.service.AcademyRequestContext;
import com.jia.academy.learning.service.MasteryRuleEngine;
import com.jia.academy.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Authenticated learner-only mastery evidence. Scores are server-derived and model-versioned. */
@RestController
@RequestMapping("/api/v1/me/mastery")
public class LearnerMasteryController {
    private final JdbcTemplate jdbc;
    private final MasteryRuleEngine mastery = new MasteryRuleEngine();

    public LearnerMasteryController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public Map<String, Object> summary(@AuthenticationPrincipal CurrentUser user,
                                       HttpServletRequest request) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required");
        }
        AcademyRequestContext context = (AcademyRequestContext) request.getAttribute(
                AcademyAppContextInterceptor.REQUEST_CONTEXT_ATTRIBUTE);
        UUID academy;
        if (context != null) {
            academy = context.academyId();
            if (user.academyIds() == null || !user.academyIds().contains(academy)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Learner is not a member of the requested academy");
            }
        } else if (user.academyIds() != null && user.academyIds().size() == 1) {
            academy = user.academyIds().getFirst();
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Registered academy-app context is required for multi-academy mastery");
        }

        List<Map<String, Object>> topics = jdbc.query("""
                SELECT c.slug,c.name,
                    (SELECT COUNT(*) FROM quiz_attempt_answers aa
                     JOIN quiz_attempts a ON a.id=aa.attempt_id
                     JOIN quiz_attempt_questions aq ON aq.attempt_id=aa.attempt_id AND aq.position=aa.position
                     JOIN questions q ON q.id=aq.question_id
                     WHERE q.category_id=c.id AND a.user_id=? AND a.academy_id=?
                       AND a.status='SUBMITTED' AND aa.is_correct IS NOT NULL) answers,
                    (SELECT COUNT(*) FROM quiz_attempt_answers aa
                     JOIN quiz_attempts a ON a.id=aa.attempt_id
                     JOIN quiz_attempt_questions aq ON aq.attempt_id=aa.attempt_id AND aq.position=aa.position
                     JOIN questions q ON q.id=aq.question_id
                     WHERE q.category_id=c.id AND a.user_id=? AND a.academy_id=?
                       AND a.status='SUBMITTED' AND aa.is_correct=TRUE) correct,
                    (SELECT COUNT(DISTINCT lp.lesson_id) FROM learner_lesson_progress lp
                     JOIN lessons l ON l.id=lp.lesson_id
                     WHERE l.category_id=c.id AND lp.user_id=? AND lp.status='COMPLETED'
                       AND l.status='PUBLISHED') completed,
                    (SELECT COUNT(*) FROM lessons l
                     WHERE l.category_id=c.id AND l.status='PUBLISHED') available,
                    (SELECT MAX(a.submitted_at) FROM quiz_attempts a
                     WHERE a.category_id=c.id AND a.user_id=? AND a.academy_id=?
                       AND a.status='SUBMITTED') last_demonstrated
                FROM categories c JOIN technologies t ON t.id=c.technology_id
                WHERE t.academy_id=? ORDER BY c.name,c.slug
                """, (rs, rowNum) -> {
                    long answers = rs.getLong("answers");
                    long correct = rs.getLong("correct");
                    int completed = Math.toIntExact(rs.getLong("completed"));
                    int available = Math.toIntExact(rs.getLong("available"));
                    var timestamp = rs.getTimestamp("last_demonstrated");
                    Instant lastDemonstrated = timestamp == null ? null : timestamp.toInstant();
                    // Confidence is intentionally neutral until a first-party confidence signal exists.
                    var result = mastery.evaluate(new MasteryRuleEngine.Evidence(
                            completed, available, Math.toIntExact(correct), Math.toIntExact(answers),
                            50, lastDemonstrated), Instant.now());
                    Map<String, Object> topic = new LinkedHashMap<>();
                    topic.put("topicSlug", rs.getString("slug"));
                    topic.put("topicName", rs.getString("name"));
                    topic.put("answers", answers);
                    topic.put("correct", correct);
                    topic.put("completedLessons", completed);
                    topic.put("availableLessons", available);
                    topic.put("masteryScore", result.score());
                    topic.put("masteryBand", result.band().name());
                    topic.put("reviewDue", result.reviewDue());
                    topic.put("lastDemonstratedAt", lastDemonstrated);
                    topic.put("masteryModelVersion", result.modelVersion());
                    return topic;
                }, user.id(), academy, user.id(), academy, user.id(), user.id(), academy, academy);

        return Map.of("modelVersion", MasteryRuleEngine.MODEL_VERSION, "topics", topics);
    }
}
