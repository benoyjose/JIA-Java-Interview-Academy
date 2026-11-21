CREATE TABLE academy_subscription_plans (
    id UUID PRIMARY KEY,
    academy_id UUID NOT NULL REFERENCES academies(id) ON DELETE CASCADE,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(140) NOT NULL,
    price_minor BIGINT NOT NULL CHECK (price_minor >= 0),
    currency CHAR(3) NOT NULL,
    billing_interval VARCHAR(20) NOT NULL CHECK (billing_interval IN ('MONTHLY','YEARLY','LIFETIME')),
    features JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (academy_id, code),
    UNIQUE (academy_id, id)
);
CREATE TABLE academy_subscriptions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    academy_id UUID NOT NULL REFERENCES academies(id) ON DELETE CASCADE,
    plan_id UUID NOT NULL,
    provider_code VARCHAR(40) NOT NULL,
    provider_subscription_id VARCHAR(180) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','GRACE','EXPIRED','CANCELLED','REFUNDED')),
    current_period_end TIMESTAMPTZ,
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (academy_id, plan_id) REFERENCES academy_subscription_plans(academy_id, id),
    UNIQUE (provider_code, provider_subscription_id),
    UNIQUE (user_id, academy_id, id)
);
CREATE INDEX idx_academy_subscriptions_user_academy_status
    ON academy_subscriptions(user_id, academy_id, status, current_period_end DESC);
CREATE TABLE academy_entitlements (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    academy_id UUID NOT NULL REFERENCES academies(id) ON DELETE CASCADE,
    entitlement_code VARCHAR(80) NOT NULL,
    subscription_id UUID,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','GRACE','EXPIRED','REVOKED')),
    valid_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, academy_id, entitlement_code),
    FOREIGN KEY (user_id, academy_id, subscription_id)
        REFERENCES academy_subscriptions(user_id, academy_id, id)
);
CREATE INDEX idx_entitlements_user_academy_validity
    ON academy_entitlements(user_id, academy_id, status, valid_until);
CREATE TABLE payment_provider_events (
    provider_code VARCHAR(40) NOT NULL,
    provider_event_id VARCHAR(180) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    processing_status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED'
        CHECK (processing_status IN ('RECEIVED','PROCESSED','IGNORED','FAILED')),
    PRIMARY KEY (provider_code, provider_event_id)
);
CREATE TABLE lesson_entitlement_requirements (
    lesson_id UUID NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    entitlement_code VARCHAR(80) NOT NULL DEFAULT 'PREMIUM',
    PRIMARY KEY (lesson_id, entitlement_code)
);
CREATE INDEX idx_lesson_entitlement_requirements_code
    ON lesson_entitlement_requirements(entitlement_code);
