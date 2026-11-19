CREATE TABLE academy_apps (
    id UUID PRIMARY KEY,
    academy_id UUID NOT NULL REFERENCES academies(id),
    app_code VARCHAR(40) NOT NULL UNIQUE,
    platform VARCHAR(12) NOT NULL,
    application_id VARCHAR(255) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    minimum_supported_version VARCHAR(40),
    current_version VARCHAR(40),
    store_url VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_academy_app_platform_identifier UNIQUE (platform, application_id),
    CONSTRAINT chk_academy_app_platform CHECK (platform IN ('ANDROID','IOS')),
    CONSTRAINT chk_academy_app_status CHECK (status IN ('PLANNED','ACTIVE','SUSPENDED','RETIRED'))
);
CREATE INDEX idx_academy_apps_academy_status ON academy_apps(academy_id,status);
-- Package names and bundle identifiers are intentionally not guessed.
-- Insert JIA Android/iOS registrations after their final identifiers are selected.
