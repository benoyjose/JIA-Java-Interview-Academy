ALTER TABLE auth_refresh_tokens
    ADD COLUMN access_token_id UUID REFERENCES api_tokens(id) ON DELETE CASCADE;
CREATE INDEX idx_refresh_tokens_access_token ON auth_refresh_tokens(access_token_id);

-- Durable mail outbox; payload is encrypted by the application before persistence.
CREATE TABLE auth_email_outbox (
    id UUID PRIMARY KEY,
    recipient VARCHAR(254) NOT NULL,
    template_key VARCHAR(80) NOT NULL,
    encrypted_payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    last_error VARCHAR(500),
    CONSTRAINT chk_auth_email_outbox_status CHECK (status IN ('PENDING','PROCESSING','SENT','FAILED'))
);
CREATE INDEX idx_auth_email_outbox_pending ON auth_email_outbox(status, available_at, created_at);
