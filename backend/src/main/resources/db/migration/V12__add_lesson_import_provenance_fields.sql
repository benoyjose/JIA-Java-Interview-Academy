ALTER TABLE lesson_revisions
    ADD COLUMN source_description VARCHAR(500),
    ADD COLUMN attribution VARCHAR(1000);

CREATE INDEX idx_lessons_category_slug
    ON lessons(category_id, slug);
