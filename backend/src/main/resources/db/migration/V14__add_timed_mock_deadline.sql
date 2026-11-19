-- Server-authoritative time limit for timed mock attempts.
-- Practice and other non-timed modes keep both fields NULL.
ALTER TABLE quiz_attempts
    ADD COLUMN duration_limit_seconds INTEGER,
    ADD COLUMN deadline_at TIMESTAMPTZ;

ALTER TABLE quiz_attempts
    ADD CONSTRAINT chk_quiz_attempt_duration_limit
    CHECK (duration_limit_seconds IS NULL OR duration_limit_seconds BETWEEN 60 AND 21600);

CREATE INDEX idx_quiz_attempts_active_deadline
    ON quiz_attempts(deadline_at)
    WHERE status = 'IN_PROGRESS' AND deadline_at IS NOT NULL;
