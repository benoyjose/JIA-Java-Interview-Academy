package com.jia.academy.privacy.service;

import com.jia.academy.security.CurrentUser;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Data portability export. Every learner-owned query is constrained by the
 * authenticated principal; credential material and global question content are excluded.
 */
@Service
public class LearnerPrivacyExportService {
    private final JdbcTemplate jdbc;

    public LearnerPrivacyExportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> export(CurrentUser principal) {
        var result = new LinkedHashMap<String, Object>();
        var userId = principal.id();

        result.put("formatVersion", 1);
        result.put("generatedAt", Instant.now().toString());
        result.put("profile", jdbc.queryForList(
                "SELECT id, name, email, status FROM users WHERE id = ?", userId));
        result.put("lessonProgress", jdbc.queryForList(
                "SELECT lesson_id, content_revision, status, progress_percent, last_position_json, started_at, completed_at, updated_at FROM learner_lesson_progress WHERE user_id = ? ORDER BY updated_at DESC",
                userId));
        result.put("bookmarks", jdbc.queryForList(
                "SELECT lesson_id, created_at FROM learner_bookmarks WHERE user_id = ? ORDER BY created_at DESC",
                userId));
        result.put("quizAttempts", jdbc.queryForList(
                "SELECT id, academy_id, technology_id, category_id, difficulty, mode, status, question_count, correct_count, score_percent, started_at, submitted_at, duration_seconds FROM quiz_attempts WHERE user_id = ? ORDER BY started_at DESC",
                userId));
        result.put("quizResponses", jdbc.queryForList("""
                SELECT qa.attempt_id, qa.position, qa.response_json, qa.is_correct,
                       qa.marks_awarded, qa.time_spent_ms, qa.answered_at
                FROM quiz_attempt_answers qa
                JOIN quiz_attempts a ON a.id = qa.attempt_id
                WHERE a.user_id = ? ORDER BY a.started_at DESC, qa.position
                """, userId));
        result.put("revisionSchedule", jdbc.queryForList(
                "SELECT academy_id, lesson_id, stage, due_at, last_completed_at, updated_at FROM learner_revision_schedules WHERE user_id = ? ORDER BY due_at",
                userId));
        result.put("notificationPreferences", jdbc.queryForList(
                "SELECT academy_id, reminders_enabled, push_enabled, quiet_start, quiet_end, timezone, updated_at FROM learner_notification_preferences WHERE user_id = ?",
                userId));
        result.put("notifications", jdbc.queryForList(
                "SELECT id, academy_id, notification_type, title, message, payload, status, scheduled_at, sent_at, created_at FROM learner_notifications WHERE user_id = ? ORDER BY created_at DESC",
                userId));
        return result;
    }
}
