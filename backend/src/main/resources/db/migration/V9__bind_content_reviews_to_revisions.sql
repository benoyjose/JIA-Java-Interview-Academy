ALTER TABLE content_review_tasks
    ADD COLUMN revision_id UUID REFERENCES lesson_revisions(id),
    ADD COLUMN reviewed_by UUID REFERENCES users(id);

CREATE INDEX idx_content_review_assignee_status
    ON content_review_tasks(assigned_reviewer, status, submitted_at DESC);
