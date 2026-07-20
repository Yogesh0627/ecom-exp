-- =============================================================================
-- V1 — Identity & Access
-- PRD §13: JWT auth, Google OAuth, RBAC, refresh tokens, password encryption.
-- =============================================================================

-- gen_random_uuid() is core since PG13; no extension is required for it.
--
-- Emails are TEXT with a unique index on lower(email), NOT citext. citext would give
-- case-insensitive comparison for free, but the JDBC driver reports it as Types#OTHER
-- while Hibernate maps String to varchar, so every entity with an email column fails
-- schema validation at startup. lower() indexing gives the same guarantee with no
-- extension and no ORM friction.

-- -----------------------------------------------------------------------------
-- users
-- -----------------------------------------------------------------------------
CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email               TEXT        NOT NULL,
    -- Nullable: users who sign in with Google only never set a password.
    password_hash       TEXT,
    full_name           TEXT        NOT NULL,
    phone               TEXT,
    status              TEXT        NOT NULL DEFAULT 'ACTIVE',
    email_verified_at   TIMESTAMPTZ,
    last_login_at       TIMESTAMPTZ,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    deleted_at          TIMESTAMPTZ,
    version             BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT users_status_chk CHECK (status IN ('ACTIVE', 'SUSPENDED', 'PENDING')),
    -- Indian mobile: optional +91, then 10 digits starting 6-9.
    CONSTRAINT users_phone_chk  CHECK (phone IS NULL OR phone ~ '^(\+91)?[6-9][0-9]{9}$')
);

-- Case-insensitive and unique among LIVE users only: "A@x.com" and "a@x.com" are the
-- same account. Partial (not a plain UNIQUE) so a soft-deleted account does not
-- permanently block that address from re-registering.
CREATE UNIQUE INDEX users_email_uq ON users (lower(email)) WHERE deleted_at IS NULL;
CREATE INDEX users_status_idx ON users (status) WHERE deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- roles / permissions  (RBAC)
-- -----------------------------------------------------------------------------
CREATE TABLE roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT        NOT NULL UNIQUE,
    description TEXT,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    deleted_at  TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0
);

CREATE TABLE permissions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Shape: "<resource>:<action>", e.g. product:write, order:refund.
    name        TEXT        NOT NULL UNIQUE,
    description TEXT,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    deleted_at  TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT permissions_name_chk CHECK (name ~ '^[a-z_]+:[a-z_]+$')
);

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles (id)       ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_roles (
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id     UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by  UUID,
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX role_permissions_permission_idx ON role_permissions (permission_id);
CREATE INDEX user_roles_role_idx ON user_roles (role_id);

-- -----------------------------------------------------------------------------
-- oauth_accounts  (Google login)
-- -----------------------------------------------------------------------------
CREATE TABLE oauth_accounts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider          TEXT        NOT NULL,
    provider_user_id  TEXT        NOT NULL,
    email             TEXT,
    linked_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT oauth_provider_chk CHECK (provider IN ('GOOGLE')),
    -- One Google account maps to exactly one user, forever.
    CONSTRAINT oauth_provider_user_uq UNIQUE (provider, provider_user_id)
);

CREATE INDEX oauth_accounts_user_idx ON oauth_accounts (user_id);

-- -----------------------------------------------------------------------------
-- refresh_tokens  (rotation + theft detection)
-- -----------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- SHA-256 of the token. The raw token is shown to the client once and never stored:
    -- a DB leak must not hand out live sessions.
    token_hash      TEXT        NOT NULL UNIQUE,
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    -- Set when this token is rotated. If a token WITH replaced_by_id set is presented
    -- again, it was replayed — revoke the whole chain for that user.
    replaced_by_id  UUID REFERENCES refresh_tokens (id) ON DELETE SET NULL,
    user_agent      TEXT,
    ip_address      INET,

    CONSTRAINT refresh_tokens_expiry_chk CHECK (expires_at > issued_at)
);

CREATE INDEX refresh_tokens_user_idx ON refresh_tokens (user_id);
-- Supports the cleanup job that deletes expired rows.
CREATE INDEX refresh_tokens_expires_idx ON refresh_tokens (expires_at) WHERE revoked_at IS NULL;

-- -----------------------------------------------------------------------------
-- one-time tokens
-- -----------------------------------------------------------------------------
CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  TEXT        NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE email_verification_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  TEXT        NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX password_reset_tokens_user_idx ON password_reset_tokens (user_id);
CREATE INDEX email_verification_tokens_user_idx ON email_verification_tokens (user_id);

-- -----------------------------------------------------------------------------
-- updated_at trigger
-- Set here rather than trusting the application: raw SQL, admin fixes, and backfills
-- must not be able to leave a stale updated_at behind.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER users_set_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER roles_set_updated_at
    BEFORE UPDATE ON roles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER permissions_set_updated_at
    BEFORE UPDATE ON permissions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER oauth_accounts_set_updated_at
    BEFORE UPDATE ON oauth_accounts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- -----------------------------------------------------------------------------
-- Seed: roles & permissions
-- Idempotent (ON CONFLICT DO NOTHING) so re-running against a partially seeded
-- database is safe.
-- -----------------------------------------------------------------------------
INSERT INTO roles (name, description) VALUES
    ('CUSTOMER', 'Shops on the storefront'),
    ('ADMIN',    'Full administrative access'),
    ('OPS',      'Inventory, warehouse and order fulfilment'),
    ('SUPPORT',  'Customer support: read orders, issue refunds')
ON CONFLICT (name) DO NOTHING;

INSERT INTO permissions (name, description) VALUES
    ('product:read',    'View products'),
    ('product:write',   'Create and edit products'),
    ('product:delete',  'Delete products'),
    ('category:write',  'Manage categories'),
    ('inventory:read',  'View stock levels'),
    ('inventory:write', 'Adjust stock, receive purchase orders'),
    ('supplier:write',  'Manage suppliers'),
    ('warehouse:write', 'Manage warehouses'),
    ('order:read',      'View orders'),
    ('order:write',     'Advance order status'),
    ('order:refund',    'Issue refunds'),
    ('coupon:write',    'Manage coupons'),
    ('review:moderate', 'Approve or reject reviews'),
    ('banner:write',    'Manage banners'),
    ('user:read',       'View user accounts'),
    ('user:write',      'Edit user accounts and roles'),
    ('analytics:read',  'View dashboards and analytics'),
    ('settings:write',  'Change platform settings')
ON CONFLICT (name) DO NOTHING;

-- ADMIN gets everything, including permissions added by later migrations.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name IN (
    'product:read', 'inventory:read', 'inventory:write', 'supplier:write',
    'warehouse:write', 'order:read', 'order:write'
)
WHERE r.name = 'OPS'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name IN (
    'order:read', 'order:refund', 'user:read', 'product:read', 'review:moderate'
)
WHERE r.name = 'SUPPORT'
ON CONFLICT DO NOTHING;

-- CUSTOMER intentionally gets no admin permissions. Storefront reads are public or
-- ownership-scoped (you can read YOUR order), which is enforced in code, not by a
-- blanket grant here.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name IN ('product:read')
WHERE r.name = 'CUSTOMER'
ON CONFLICT DO NOTHING;
