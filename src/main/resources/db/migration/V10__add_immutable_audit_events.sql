CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    actor_id UUID NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID,
    action VARCHAR(50) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    before_summary TEXT,
    after_summary TEXT NOT NULL
);

CREATE INDEX idx_audit_tenant_occurred
    ON audit_events (tenant_id, occurred_at DESC);

CREATE INDEX idx_audit_tenant_entity
    ON audit_events (tenant_id, entity_type, entity_id, occurred_at DESC);

CREATE OR REPLACE FUNCTION prevent_audit_event_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit events are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_events_prevent_update
    BEFORE UPDATE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_event_mutation();

CREATE TRIGGER audit_events_prevent_delete
    BEFORE DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_event_mutation();

INSERT INTO tenant_role_permissions (role_id, permission)
SELECT role.id, 'AUDIT_READ'
FROM tenant_roles role
WHERE role.template_code IN ('OWNER', 'MANAGER')
ON CONFLICT (role_id, permission) DO NOTHING;
