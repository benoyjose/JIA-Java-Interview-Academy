package com.jia.academy.learning.api;

import com.jia.academy.learning.service.StudyPlanService;
import com.jia.academy.learning.service.StudyPlanService.Task;
import com.jia.academy.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/me/study-plan")
public class StudyPlanController {
    private final JdbcTemplate jdbc;
    private final StudyPlanService planner;

    public StudyPlanController(JdbcTemplate jdbc, StudyPlanService planner) {
        this.jdbc = jdbc;
        this.planner = planner;
    }

    @GetMapping
    public Map<String, Object> show(@AuthenticationPrincipal CurrentUser user,
                                    HttpServletRequest request,
                                    @RequestParam(defaultValue = "30") int dailyMinutes) {
        UUID academy = LearnerAcademyContext.resolve(user, request);
        if (dailyMinutes < 10 || dailyMinutes > 180)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dailyMinutes must be between 10 and 180");

        List<Task> candidates = new ArrayList<>();
        // Due reviews are placed before routine continuation/practice. The server owns due dates.
        jdbc.query("""
            SELECT l.slug,l.title,c.slug topic_slug,s.due_at
            FROM learner_revision_schedules s
            JOIN lessons l ON l.id=s.lesson_id
            JOIN categories c ON c.id=l.category_id
            WHERE s.user_id=? AND s.academy_id=? AND s.due_at<=now()
              AND l.status='PUBLISHED'
              AND NOT EXISTS (
                  SELECT 1 FROM lesson_prerequisites lp
                  WHERE lp.lesson_id=l.id
                    AND NOT EXISTS (
                        SELECT 1 FROM learner_lesson_progress done
                        WHERE done.user_id=s.user_id AND done.lesson_id=lp.prerequisite_lesson_id
                          AND done.status='COMPLETED'
                    )
              )
            ORDER BY s.due_at ASC,l.slug ASC LIMIT 50
            """, (RowCallbackHandler) rs -> candidates.add(new Task("SPACED_REVIEW", "Review " + rs.getString("title"),
                rs.getString("slug"), rs.getString("topic_slug"), 10,
                "Scheduled review is due", 100)), user.id(), academy);

        jdbc.query("""
            SELECT l.slug,l.title,p.progress_percent
            FROM learner_lesson_progress p JOIN lessons l ON l.id=p.lesson_id
            JOIN categories c ON c.id=l.category_id JOIN technologies t ON t.id=c.technology_id
            WHERE p.user_id=? AND t.academy_id=? AND l.status='PUBLISHED' AND p.progress_percent<100
              AND NOT EXISTS (
                  SELECT 1 FROM lesson_prerequisites lp
                  WHERE lp.lesson_id=l.id
                    AND NOT EXISTS (
                        SELECT 1 FROM learner_lesson_progress done
                        WHERE done.user_id=p.user_id AND done.lesson_id=lp.prerequisite_lesson_id
                          AND done.status='COMPLETED'
                    )
              )
            ORDER BY p.updated_at DESC,l.slug ASC LIMIT 100
            """, rs -> {
            double progress = rs.getBigDecimal("progress_percent").doubleValue();
            candidates.add(new Task("CONTINUE_LESSON", "Continue " + rs.getString("title"),
                rs.getString("slug"), null, 15,
                progress > 0 ? "Resume from " + Math.round(progress) + "% complete" : "Start a published lesson",
                25 + (int) Math.round(progress / 4)));
        }, user.id(), academy);

        jdbc.query("""
            SELECT c.slug,c.name,ROUND(AVG(CASE WHEN aa.is_correct THEN 100.0 ELSE 0.0 END)) AS accuracy
            FROM quiz_attempts a JOIN quiz_attempt_questions aq ON aq.attempt_id=a.id
            JOIN quiz_attempt_answers aa ON aa.attempt_id=aq.attempt_id AND aa.position=aq.position
            JOIN questions q ON q.id=aq.question_id JOIN categories c ON c.id=q.category_id
            WHERE a.user_id=? AND a.academy_id=? AND a.status='SUBMITTED'
            GROUP BY c.slug,c.name ORDER BY AVG(CASE WHEN aa.is_correct THEN 1.0 ELSE 0.0 END) ASC LIMIT 20
            """, rs -> {
            int accuracy = rs.getInt("accuracy");
            if (accuracy >= 80) return;
            candidates.add(new Task("PRACTICE_TOPIC", "Practice " + rs.getString("name"), null,
                rs.getString("slug"), 10, "Topic accuracy is " + accuracy + "%",
                StudyPlanService.topicPriority(accuracy)));
        }, user.id(), academy);

        // A first-time learner must receive a useful start rather than an empty plan.
        if (candidates.isEmpty()) {
            jdbc.query("""
                SELECT l.slug,l.title,c.slug topic_slug
                FROM lessons l JOIN categories c ON c.id=l.category_id
                JOIN technologies t ON t.id=c.technology_id
                WHERE t.academy_id=? AND l.status='PUBLISHED'
                  AND NOT EXISTS (
                      SELECT 1 FROM lesson_prerequisites lp
                      WHERE lp.lesson_id=l.id
                        AND NOT EXISTS (
                            SELECT 1 FROM learner_lesson_progress done
                            WHERE done.user_id=? AND done.lesson_id=lp.prerequisite_lesson_id
                              AND done.status='COMPLETED'
                        )
                  )
                ORDER BY t.sort_order ASC,c.sort_order ASC,l.slug ASC LIMIT 5
                """, (RowCallbackHandler) rs -> candidates.add(new Task("START_LESSON", "Start " + rs.getString("title"),
                    rs.getString("slug"), rs.getString("topic_slug"), 15,
                    "Recommended starting point for a new learner", 50)), academy);
        }

        return Map.of("academyId", academy.toString(), "dailyMinutes", dailyMinutes,
            "tasks", planner.allocate(candidates, dailyMinutes));
    }
}
