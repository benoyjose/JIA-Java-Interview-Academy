CREATE TABLE media_assets (
    id UUID PRIMARY KEY,
    academy_id UUID NOT NULL REFERENCES academies(id),
    lesson_id UUID REFERENCES lessons(id) ON DELETE SET NULL,
    object_key VARCHAR(512) NOT NULL UNIQUE,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'READY')),
    uploaded_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT chk_media_asset_completion CHECK (
        (status = 'PENDING' AND completed_at IS NULL)
        OR (status = 'READY' AND completed_at IS NOT NULL)
    )
);
CREATE INDEX idx_media_assets_academy_created ON media_assets(academy_id, created_at DESC);
CREATE INDEX idx_media_assets_lesson_created ON media_assets(lesson_id, created_at DESC)
    WHERE lesson_id IS NOT NULL;
