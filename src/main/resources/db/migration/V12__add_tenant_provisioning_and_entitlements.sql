ALTER TABLE tenants
    ADD COLUMN subscription_status VARCHAR(30) NOT NULL DEFAULT 'TRIAL',
    ADD COLUMN plan_code VARCHAR(50) NOT NULL DEFAULT 'STARTER',
    ADD COLUMN seat_limit INTEGER NOT NULL DEFAULT 5,
    ADD CONSTRAINT ck_tenant_subscription_status
        CHECK (subscription_status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'CANCELLED')),
    ADD CONSTRAINT ck_tenant_plan_code
        CHECK (plan_code ~ '^[A-Z][A-Z0-9_]*$'),
    ADD CONSTRAINT ck_tenant_seat_limit
        CHECK (seat_limit > 0);

CREATE TABLE tenant_entitlements (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    feature_key VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL,
    CONSTRAINT uk_tenant_entitlement_feature UNIQUE (tenant_id, feature_key),
    CONSTRAINT ck_tenant_entitlement_feature_key
        CHECK (feature_key ~ '^[A-Z][A-Z0-9_]*$')
);

CREATE INDEX idx_tenant_entitlement_tenant
    ON tenant_entitlements (tenant_id, feature_key);
