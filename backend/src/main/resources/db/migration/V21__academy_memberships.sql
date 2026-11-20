CREATE TABLE academy_memberships (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    academy_id UUID NOT NULL REFERENCES academies(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, academy_id),
    CONSTRAINT chk_academy_membership_status CHECK (status IN ('ACTIVE','SUSPENDED','REVOKED'))
);
CREATE INDEX idx_academy_memberships_academy_status_user
    ON academy_memberships(academy_id, status, user_id);
CREATE INDEX idx_academy_memberships_user_status
    ON academy_memberships(user_id, status, academy_id);

INSERT INTO academy_memberships(user_id, academy_id)
SELECT u.id, a.id FROM users u CROSS JOIN academies a
WHERE a.code = 'JIA' AND a.status = 'ACTIVE';

CREATE OR REPLACE FUNCTION assign_default_academy_membership()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO academy_memberships(user_id, academy_id)
    SELECT NEW.id, a.id FROM academies a
    WHERE a.code = 'JIA' AND a.status = 'ACTIVE'
    ON CONFLICT (user_id, academy_id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_default_academy_membership
AFTER INSERT ON users
FOR EACH ROW EXECUTE FUNCTION assign_default_academy_membership();
