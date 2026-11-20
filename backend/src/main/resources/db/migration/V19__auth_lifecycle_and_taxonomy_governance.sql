-- CMS taxonomy governance and credential lifecycle primitives.
-- Secrets are stored only as SHA-256 digests; raw tokens are returned once.
CREATE TABLE auth_refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    family_id UUID NOT NULL,
    token_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ
);
CREATE INDEX idx_refresh_tokens_user_expiry ON auth_refresh_tokens(user_id, expires_at);
CREATE INDEX idx_refresh_tokens_family ON auth_refresh_tokens(family_id);

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);
CREATE INDEX idx_password_reset_user_expiry ON password_reset_tokens(user_id, expires_at);

-- Taxonomy is soft-deleted to preserve lesson and question provenance.
CREATE INDEX idx_categories_parent_order ON categories(parent_id, sort_order);
CREATE INDEX idx_categories_technology_slug ON categories(technology_id, slug);
