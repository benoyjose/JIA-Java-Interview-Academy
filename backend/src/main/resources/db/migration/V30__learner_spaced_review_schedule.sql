CREATE TABLE learner_spaced_reviews (
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 lesson_id UUID NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
 repetitions SMALLINT NOT NULL DEFAULT 0 CHECK (repetitions >= 0),
 ease_factor NUMERIC(3,2) NOT NULL DEFAULT 2.50 CHECK (ease_factor BETWEEN 1.30 AND 3.00),
 interval_days SMALLINT NOT NULL DEFAULT 0 CHECK (interval_days BETWEEN 0 AND 365),
 last_rating SMALLINT CHECK (last_rating BETWEEN 0 AND 5),
 last_reviewed_at TIMESTAMPTZ,
 due_date DATE NOT NULL DEFAULT CURRENT_DATE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(user_id,lesson_id)
);
CREATE INDEX idx_spaced_reviews_user_due ON learner_spaced_reviews(user_id,due_date,lesson_id);
