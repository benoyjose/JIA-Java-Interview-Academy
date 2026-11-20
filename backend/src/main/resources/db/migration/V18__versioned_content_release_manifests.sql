CREATE TABLE content_releases (
    version BIGINT PRIMARY KEY,
    manifest_json JSONB NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
