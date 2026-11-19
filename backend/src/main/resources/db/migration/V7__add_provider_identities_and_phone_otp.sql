-- Provider-neutral identities. Existing email/password accounts remain in users;
-- provider identities are linked by immutable provider subject, never by email alone.
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

CREATE TABLE auth_identities (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(20) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    provider_email VARCHAR(254),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_authenticated_at TIMESTAMPTZ,
    CONSTRAINT uq_auth_identity_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT chk_auth_identity_provider CHECK (provider IN ('GOOGLE','APPLE','PHONE','EMAIL'))
);
CREATE INDEX idx_auth_identities_user ON auth_identities(user_id);

-- OTP challenges store only an HMAC digest, never the plaintext code.
CREATE TABLE phone_otp_challenges (
    id UUID PRIMARY KEY,
    phone_e164 VARCHAR(16) NOT NULL,
    code_digest CHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_phone_otp_attempts CHECK (attempts >= 0 AND attempts <= 10)
);
CREATE INDEX idx_phone_otp_lookup ON phone_otp_challenges(phone_e164, created_at DESC);
