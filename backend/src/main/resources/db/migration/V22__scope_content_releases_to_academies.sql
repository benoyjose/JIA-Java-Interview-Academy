ALTER TABLE content_releases
    ADD COLUMN academy_id UUID REFERENCES academies(id);

UPDATE content_releases
SET academy_id = '50000000-0000-0000-0000-000000000001';

ALTER TABLE content_releases
    ALTER COLUMN academy_id SET NOT NULL;

ALTER TABLE content_releases
    DROP CONSTRAINT content_releases_pkey;

ALTER TABLE content_releases
    ADD CONSTRAINT pk_content_releases PRIMARY KEY (academy_id, version);

CREATE INDEX idx_content_releases_academy_created
    ON content_releases(academy_id, created_at DESC);
