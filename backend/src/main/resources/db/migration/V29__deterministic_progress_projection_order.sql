-- Make last-position projection deterministic when clients replay out-of-order events.
ALTER TABLE learner_lesson_progress
    ADD COLUMN projection_event_at TIMESTAMPTZ NOT NULL DEFAULT '-infinity',
    ADD COLUMN projection_event_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000';
