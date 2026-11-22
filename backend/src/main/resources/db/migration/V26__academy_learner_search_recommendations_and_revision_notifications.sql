CREATE TABLE learner_notification_preferences (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    academy_id UUID NOT NULL REFERENCES academies(id) ON DELETE CASCADE,
    reminders_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    push_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    quiet_start TIME,
    quiet_end TIME,
    timezone VARCHAR(80) NOT NULL DEFAULT 'UTC',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(user_id,academy_id),
    CHECK ((quiet_start IS NULL) = (quiet_end IS NULL))
);
CREATE TABLE learner_revision_schedules (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    academy_id UUID NOT NULL REFERENCES academies(id) ON DELETE CASCADE,
    lesson_id UUID NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    stage SMALLINT NOT NULL DEFAULT 0 CHECK(stage BETWEEN 0 AND 5),
    due_at TIMESTAMPTZ NOT NULL,
    last_completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(user_id,academy_id,lesson_id)
);
CREATE INDEX idx_revision_schedule_due ON learner_revision_schedules(due_at,academy_id);
CREATE TABLE learner_notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    academy_id UUID NOT NULL REFERENCES academies(id) ON DELETE CASCADE,
    notification_type VARCHAR(40) NOT NULL,
    dedupe_key VARCHAR(180) NOT NULL,
    title VARCHAR(180) NOT NULL,
    message VARCHAR(700) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK(status IN ('PENDING','SENT','READ','SKIPPED')),
    scheduled_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id,academy_id,dedupe_key)
);
CREATE INDEX idx_learner_notifications_inbox ON learner_notifications(user_id,academy_id,status,created_at DESC);
