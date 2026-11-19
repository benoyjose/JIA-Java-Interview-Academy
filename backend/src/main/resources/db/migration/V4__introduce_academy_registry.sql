CREATE TABLE academies (
    id UUID PRIMARY KEY,
    code VARCHAR(12) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    branding_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_academies_status CHECK (status IN ('ACTIVE','INACTIVE','DRAFT'))
);

INSERT INTO academies(id, code, name, description, status)
VALUES ('50000000-0000-0000-0000-000000000001','JIA','Java Interview Academy',
        'Java learning and interview preparation academy.','ACTIVE');

ALTER TABLE technologies ADD COLUMN academy_id UUID;
UPDATE technologies SET academy_id = '50000000-0000-0000-0000-000000000001';
ALTER TABLE technologies ALTER COLUMN academy_id SET NOT NULL;
ALTER TABLE technologies ADD CONSTRAINT fk_technologies_academy
    FOREIGN KEY (academy_id) REFERENCES academies(id);
ALTER TABLE technologies DROP CONSTRAINT technologies_slug_key;
ALTER TABLE technologies ADD CONSTRAINT uq_technologies_academy_slug UNIQUE (academy_id, slug);
CREATE INDEX idx_technologies_academy_status_order
    ON technologies(academy_id, status, sort_order);
