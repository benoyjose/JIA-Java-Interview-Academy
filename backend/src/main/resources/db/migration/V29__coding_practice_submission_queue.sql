-- Control-plane persistence only. No submitted source is compiled or run by the API.
CREATE TABLE coding_submissions (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 academy_id UUID NOT NULL REFERENCES academies(id), exercise_id UUID NOT NULL,
 language VARCHAR(16) NOT NULL, source_code TEXT NOT NULL, source_sha256 CHAR(64) NOT NULL,
 idempotency_key UUID NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
 execution_mode VARCHAR(16) NOT NULL DEFAULT 'DISABLED', cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT uq_coding_submission_idempotency UNIQUE(user_id,academy_id,idempotency_key),
 CONSTRAINT chk_coding_submission_language CHECK(language IN ('JAVA')),
 CONSTRAINT chk_coding_submission_status CHECK(status IN ('QUEUED','RUNNING','COMPLETED','COMPILE_ERROR','TIME_LIMIT_EXCEEDED','MEMORY_LIMIT_EXCEEDED','CANCELLED','INTERNAL_ERROR')),
 CONSTRAINT chk_coding_submission_mode CHECK(execution_mode IN ('DISABLED','SANDBOX')),
 CONSTRAINT chk_coding_submission_source_size CHECK(octet_length(source_code)<=65536)
);
CREATE INDEX idx_coding_submissions_owner_created ON coding_submissions(user_id,academy_id,created_at DESC);
CREATE INDEX idx_coding_submissions_queue ON coding_submissions(status,created_at) WHERE status='QUEUED';
