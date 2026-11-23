package com.jia.academy.learning.api;

import com.jia.academy.learning.service.StudyPlanService;
import com.jia.academy.learning.service.StudyPlanService.Task;
import com.jia.academy.security.CurrentUser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
                                    @RequestParam(defaultValue = "30") int dailyMinutes) {
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        if (dailyMinutes < 10 || dailyMinutes > 180)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dailyMinutes must be between 10 and 180");
        UUID academy = UUID.fromString("50000000-0000-0000-0000-000000000001");
        List<Task> candidates = new ArrayList<>();
        jdbc.query("""
            SELECT l.slug,l.title,p.progress_percent
            FROM learner_lesson_progress p JOIN lessons l ON l.id=p.lesson_id
            JOIN categories c ON c.id=l.category_id JOIN technologies t ON t.id=c.technology_id
            WHERE p.user_id=? AND t.academy_id=? AND l.status='PUBLISHED' AND p.progress_percent<100
            ORDER BY p.updated_at DESC LIMIT 100
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
                    rs.getString("slug"), 10, "Topic accuracy is " + accuracy + "%", StudyPlanService.topicPriority(accuracy)));
        }, user.id(), academy);
        return Map.of("dailyMinutes", dailyMinutes, "tasks", planner.allocate(candidates, dailyMinutes));
    }
}
