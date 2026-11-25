-- M1: explicit curriculum dependency edges used by prerequisite-aware study plans.
-- Edges are directed: lesson_id cannot be recommended until prerequisite_lesson_id is complete.
CREATE TABLE lesson_prerequisites (
    lesson_id UUID NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    prerequisite_lesson_id UUID NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (lesson_id, prerequisite_lesson_id),
    CONSTRAINT chk_lesson_prerequisite_not_self CHECK (lesson_id <> prerequisite_lesson_id)
);
CREATE INDEX idx_lesson_prerequisites_reverse
    ON lesson_prerequisites(prerequisite_lesson_id, lesson_id);
COMMENT ON TABLE lesson_prerequisites IS
    'Versioned curriculum dependency edges. Publication validation must reject cycles and cross-academy references.';
