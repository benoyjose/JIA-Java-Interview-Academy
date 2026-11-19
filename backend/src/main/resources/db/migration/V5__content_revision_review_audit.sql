CREATE TABLE lesson_revisions (
    id UUID PRIMARY KEY,
    lesson_id UUID NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    revision_number INTEGER NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(700),
    difficulty VARCHAR(20) NOT NULL,
    content_json JSONB,
    source_type VARCHAR(30) NOT NULL,
    changed_by UUID REFERENCES users(id),
    change_note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_lesson_revision UNIQUE (lesson_id, revision_number)
);
INSERT INTO lesson_revisions(id, lesson_id, revision_number, title, summary, difficulty, content_json, source_type, change_note)
SELECT gen_random_uuid(), id, content_revision, title, summary, difficulty, content_json, source_type,
       'Baseline revision captured during content versioning rollout'
FROM lessons;

CREATE TABLE content_review_tasks (
    id UUID PRIMARY KEY,
    lesson_id UUID NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    submitted_by UUID NOT NULL REFERENCES users(id),
    assigned_reviewer UUID REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    review_comment TEXT,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at TIMESTAMPTZ,
    CONSTRAINT chk_content_review_status CHECK (status IN ('PENDING','APPROVED','CHANGES_REQUESTED','REJECTED'))
);
CREATE INDEX idx_content_review_status_submitted ON content_review_tasks(status, submitted_at);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    actor_user_id UUID REFERENCES users(id),
    academy_id UUID REFERENCES academies(id),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id UUID,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_events_academy_time ON audit_events(academy_id, occurred_at DESC);
CREATE INDEX idx_audit_events_actor_time ON audit_events(actor_user_id, occurred_at DESC);
