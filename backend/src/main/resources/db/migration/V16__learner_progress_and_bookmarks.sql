CREATE TABLE learner_lesson_progress (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    lesson_id UUID NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    content_revision INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    progress_percent NUMERIC(5,2) NOT NULL DEFAULT 0,
    last_position_json JSONB,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, lesson_id),
    CONSTRAINT chk_lesson_progress_status CHECK (status IN ('IN_PROGRESS','COMPLETED')),
    CONSTRAINT chk_lesson_progress_percent CHECK (progress_percent BETWEEN 0 AND 100)
);
CREATE INDEX idx_lesson_progress_user_updated
    ON learner_lesson_progress(user_id, updated_at DESC);

CREATE TABLE learner_bookmarks (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    lesson_id UUID NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, lesson_id)
);
CREATE INDEX idx_learner_bookmarks_user_created
    ON learner_bookmarks(user_id, created_at DESC);
