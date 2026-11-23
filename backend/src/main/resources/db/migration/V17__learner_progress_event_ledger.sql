-- Append-only learner activity ledger; aggregate progress remains in V16 projection.
CREATE TABLE learner_progress_events (
 id BIGSERIAL PRIMARY KEY,
 event_id UUID NOT NULL,
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 academy_code VARCHAR(40) NOT NULL,
 event_type VARCHAR(32) NOT NULL,
 schema_version INTEGER NOT NULL DEFAULT 1,
 occurred_at TIMESTAMPTZ NOT NULL,
 received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 lesson_id UUID REFERENCES lessons(id) ON DELETE SET NULL,
 payload JSONB NOT NULL DEFAULT '{}'::jsonb,
 CONSTRAINT uq_progress_event_identity UNIQUE (user_id, academy_code, event_id),
 CONSTRAINT chk_progress_event_type CHECK (event_type IN ('LESSON_STARTED','LESSON_COMPLETED','QUESTION_ANSWERED','QUIZ_SUBMITTED','BOOKMARK_CHANGED','REVIEW_COMPLETED')),
 CONSTRAINT chk_progress_event_schema CHECK (schema_version BETWEEN 1 AND 10),
 CONSTRAINT chk_progress_event_payload_size CHECK (pg_column_size(payload) <= 16384)
);
CREATE INDEX idx_progress_events_learner_time ON learner_progress_events(user_id, academy_code, occurred_at DESC, event_id);
CREATE INDEX idx_progress_events_lesson_time ON learner_progress_events(user_id, lesson_id, occurred_at DESC) WHERE lesson_id IS NOT NULL;
COMMENT ON TABLE learner_progress_events IS 'Append-only client activity ledger. Never store answer keys, credentials or mastery scores in payload.';
