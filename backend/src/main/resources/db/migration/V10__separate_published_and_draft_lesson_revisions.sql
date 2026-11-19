ALTER TABLE lessons
    ADD COLUMN published_revision_id UUID REFERENCES lesson_revisions(id),
    ADD COLUMN draft_revision_id UUID REFERENCES lesson_revisions(id);

-- Preserve the exact content currently visible to learners before revision pointers
-- become the source of truth for catalogue responses.
UPDATE lessons l
SET published_revision_id = r.id
FROM lesson_revisions r
WHERE r.lesson_id = l.id
  AND r.revision_number = l.content_revision
  AND l.status = 'PUBLISHED';

ALTER TABLE lessons
    ADD CONSTRAINT chk_lesson_revision_pointers_distinct
    CHECK (published_revision_id IS NULL OR draft_revision_id IS NULL
           OR published_revision_id <> draft_revision_id);

CREATE INDEX idx_lessons_published_revision ON lessons(published_revision_id)
    WHERE published_revision_id IS NOT NULL;
