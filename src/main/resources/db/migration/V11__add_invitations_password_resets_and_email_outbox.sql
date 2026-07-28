CREATE TABLE tenant_invitations (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    user_id UUID NOT NULL REFERENCES tenant_users(id),
    role_id UUID NOT NULL REFERENCES tenant_roles(id),
    invited_by UUID NOT NULL,
    email VARCHAR(150) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    CONSTRAINT ck_tenant_invitation_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED'))
);

CREATE INDEX idx_invitation_tenant_created
    ON tenant_invitations (tenant_id, created_at DESC);
CREATE INDEX idx_invitation_user_status
    ON tenant_invitations (user_id, status);
CREATE INDEX idx_invitation_expiry
    ON tenant_invitations (status, expires_at);

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    user_id UUID NOT NULL REFERENCES tenant_users(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    CONSTRAINT ck_password_reset_status
        CHECK (status IN ('PENDING', 'CONSUMED', 'REVOKED', 'EXPIRED'))
);

CREATE INDEX idx_password_reset_user_status
    ON password_reset_tokens (user_id, status);
CREATE INDEX idx_password_reset_expiry
    ON password_reset_tokens (status, expires_at);

CREATE TABLE email_outbox_messages (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    recipient VARCHAR(150) NOT NULL,
    template VARCHAR(40) NOT NULL,
    reference_id UUID NOT NULL,
    token_ciphertext TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    delivered_at TIMESTAMPTZ,
    last_error VARCHAR(255),
    CONSTRAINT ck_email_outbox_status
        CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_email_outbox_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_email_outbox_delivery
    ON email_outbox_messages (status, next_attempt_at, created_at);
CREATE INDEX idx_email_outbox_tenant_created
    ON email_outbox_messages (tenant_id, created_at DESC);
