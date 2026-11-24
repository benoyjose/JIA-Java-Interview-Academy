package com.jia.academy.learning.api;

import com.jia.academy.security.CurrentUser;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Stores learner-owned study targets and reminder window preferences.
 * Delivery scheduling and push-token lifecycle are separate follow-up slices.
 */
@RestController
@RequestMapping("/api/v1/me/reminder-preferences")
public class LearnerReminderPreferencesController {
    private final JdbcTemplate jdbc;

    public LearnerReminderPreferencesController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public Map<String, Object> get(@AuthenticationPrincipal CurrentUser user) {
        requireUser(user);
        return jdbc.query("""
            SELECT enabled, timezone, reminder_time, quiet_start, quiet_end,
                   daily_goal_minutes, weekly_goal_days, updated_at
            FROM learner_reminder_preferences WHERE user_id=?
            """, rs -> {
            if (!rs.next()) return defaultPreferences();
            return Map.of(
                "enabled", rs.getBoolean("enabled"),
                "timezone", rs.getString("timezone"),
                "reminderTime", rs.getTime("reminder_time").toLocalTime().toString(),
                "quietStart", rs.getTime("quiet_start") == null ? "" : rs.getTime("quiet_start").toLocalTime().toString(),
                "quietEnd", rs.getTime("quiet_end") == null ? "" : rs.getTime("quiet_end").toLocalTime().toString(),
                "dailyGoalMinutes", rs.getInt("daily_goal_minutes"),
                "weeklyGoalDays", rs.getInt("weekly_goal_days"),
                "updatedAt", rs.getTimestamp("updated_at").toInstant().toString()
            );
        }, user.id());
    }

    @PutMapping
    public Map<String, Object> update(@AuthenticationPrincipal CurrentUser user,
                                      @RequestBody ReminderPreferencesRequest request) {
        requireUser(user);
        validate(request);
        jdbc.update("""
            INSERT INTO learner_reminder_preferences
                (user_id,enabled,timezone,reminder_time,quiet_start,quiet_end,
                 daily_goal_minutes,weekly_goal_days,updated_at)
            VALUES (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
            ON CONFLICT(user_id) DO UPDATE SET
                enabled=EXCLUDED.enabled, timezone=EXCLUDED.timezone,
                reminder_time=EXCLUDED.reminder_time, quiet_start=EXCLUDED.quiet_start,
                quiet_end=EXCLUDED.quiet_end, daily_goal_minutes=EXCLUDED.daily_goal_minutes,
                weekly_goal_days=EXCLUDED.weekly_goal_days, updated_at=CURRENT_TIMESTAMP
            """, user.id(), request.enabled(), request.timezone(), LocalTime.parse(request.reminderTime()),
            nullableTime(request.quietStart()), nullableTime(request.quietEnd()),
            request.dailyGoalMinutes(), request.weeklyGoalDays());
        return get(user);
    }

    static void validate(ReminderPreferencesRequest request) {
        if (request == null) badRequest("Request body is required");
        try {
            ZoneId.of(request.timezone());
            LocalTime.parse(request.reminderTime());
            if (!blank(request.quietStart())) LocalTime.parse(request.quietStart());
            if (!blank(request.quietEnd())) LocalTime.parse(request.quietEnd());
        } catch (RuntimeException ex) {
            badRequest("timezone and time fields must use valid IANA/ISO values");
        }
        if (blank(request.timezone()) || request.timezone().length() > 64)
            badRequest("timezone is required and must be at most 64 characters");
        if (request.dailyGoalMinutes() < 0 || request.dailyGoalMinutes() > 240)
            badRequest("dailyGoalMinutes must be between 0 and 240");
        if (request.weeklyGoalDays() < 1 || request.weeklyGoalDays() > 7)
            badRequest("weeklyGoalDays must be between 1 and 7");
        if (blank(request.quietStart()) != blank(request.quietEnd()))
            badRequest("quietStart and quietEnd must both be set or both be empty");
    }

    private static Map<String, Object> defaultPreferences() {
        return Map.of("enabled", false, "timezone", "UTC", "reminderTime", "19:00",
            "quietStart", "", "quietEnd", "", "dailyGoalMinutes", 30, "weeklyGoalDays", 5);
    }

    private static LocalTime nullableTime(String value) {
        return blank(value) ? null : LocalTime.parse(value);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static void requireUser(CurrentUser user) {
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
    }

    private static void badRequest(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record ReminderPreferencesRequest(
        boolean enabled,
        String timezone,
        String reminderTime,
        String quietStart,
        String quietEnd,
        int dailyGoalMinutes,
        int weeklyGoalDays
    ) {}
}
