CREATE TABLE learner_reminder_preferences (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT false,
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    reminder_time TIME NOT NULL DEFAULT '19:00',
    quiet_start TIME,
    quiet_end TIME,
    daily_goal_minutes INTEGER NOT NULL DEFAULT 30 CHECK (daily_goal_minutes BETWEEN 0 AND 240),
    weekly_goal_days INTEGER NOT NULL DEFAULT 5 CHECK (weekly_goal_days BETWEEN 1 AND 7),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT learner_reminder_quiet_hours_pair CHECK (
        (quiet_start IS NULL AND quiet_end IS NULL) OR
        (quiet_start IS NOT NULL AND quiet_end IS NOT NULL)
    )
);
CREATE INDEX learner_reminder_preferences_enabled_idx
    ON learner_reminder_preferences (timezone, reminder_time)
    WHERE enabled = true;
