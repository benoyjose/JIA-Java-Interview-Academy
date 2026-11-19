-- Stable pagination order for the content library and pending review queue.
CREATE INDEX IF NOT EXISTS idx_lessons_admin_updated
    ON lessons (updated_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_lessons_admin_status_updated
    ON lessons (status, updated_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_content_review_pending_submitted
    ON content_review_tasks (submitted_at ASC, id ASC)
    WHERE status = 'PENDING';

-- Prefix search keeps admin filtering indexable without requiring pg_trgm.
CREATE INDEX IF NOT EXISTS idx_lessons_title_prefix
    ON lessons (lower(title) text_pattern_ops);

CREATE INDEX IF NOT EXISTS idx_lessons_slug_prefix
    ON lessons (lower(slug) text_pattern_ops);

CREATE INDEX IF NOT EXISTS idx_categories_slug_prefix
    ON categories (lower(slug) text_pattern_ops);

CREATE INDEX IF NOT EXISTS idx_users_name_prefix
    ON users (lower(name) text_pattern_ops);