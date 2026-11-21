-- Register the already configured JIA Android package as a planned store app.
-- Planned state prevents public client policy from treating it as a released binary.
INSERT INTO academy_apps (
    id, academy_id, app_code, platform, application_id, display_name, status,
    minimum_supported_version, current_version, store_url
)
VALUES (
    '60000000-0000-0000-0000-000000000001',
    '50000000-0000-0000-0000-000000000001',
    'JIA_ANDROID', 'ANDROID', 'com.jia.academy.app',
    'Java Interview Academy (Android)', 'PLANNED', '0.1.0', '0.1.0', NULL
)
ON CONFLICT (app_code) DO NOTHING;

ALTER TABLE academy_apps
    ADD COLUMN release_notes TEXT,
    ADD COLUMN released_at TIMESTAMPTZ,
    ADD CONSTRAINT chk_academy_app_version_length
        CHECK (minimum_supported_version IS NULL OR length(minimum_supported_version) <= 40),
    ADD CONSTRAINT chk_academy_app_current_version_length
        CHECK (current_version IS NULL OR length(current_version) <= 40),
    CONSTRAINT chk_academy_app_release_notes_length
        CHECK (release_notes IS NULL OR length(release_notes) <= 5000);
