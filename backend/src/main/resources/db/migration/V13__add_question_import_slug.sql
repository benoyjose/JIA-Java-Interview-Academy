-- Stable, portable question key scoped to its curriculum category.
ALTER TABLE questions ADD COLUMN slug VARCHAR(120);
CREATE UNIQUE INDEX uq_questions_category_slug ON questions(category_id, slug) WHERE slug IS NOT NULL;
