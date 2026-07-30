SET search_path TO zentro_desk;

ALTER TABLE quotations
    ADD COLUMN assigned_user_id UUID;

UPDATE quotations quotation
SET assigned_user_id = lead.assigned_user_id
FROM leads lead
WHERE quotation.lead_id = lead.id;

ALTER TABLE quotations
    ADD CONSTRAINT fk_quotation_owner_tenant
        FOREIGN KEY (assigned_user_id, tenant_id)
        REFERENCES tenant_users(id, tenant_id);

CREATE INDEX idx_quotation_owner
    ON quotations (tenant_id, assigned_user_id, created_at DESC);

ALTER TABLE bookings
    ADD COLUMN lead_id UUID REFERENCES leads(id),
    ADD COLUMN assigned_user_id UUID;

UPDATE bookings booking
SET lead_id = quotation.lead_id,
    assigned_user_id = quotation.assigned_user_id
FROM quotations quotation
WHERE booking.quotation_id = quotation.id;

ALTER TABLE bookings
    ADD CONSTRAINT fk_booking_owner_tenant
        FOREIGN KEY (assigned_user_id, tenant_id)
        REFERENCES tenant_users(id, tenant_id);

CREATE INDEX idx_booking_lead
    ON bookings (tenant_id, lead_id);
CREATE INDEX idx_booking_owner
    ON bookings (tenant_id, assigned_user_id, created_at DESC);

ALTER TABLE customer_conversations
    ADD COLUMN lead_id UUID REFERENCES leads(id),
    ADD COLUMN quotation_id UUID REFERENCES quotations(id),
    ADD COLUMN booking_id UUID REFERENCES bookings(id);

CREATE INDEX idx_customer_conversation_lead
    ON customer_conversations (tenant_id, lead_id);
CREATE INDEX idx_customer_conversation_quotation
    ON customer_conversations (tenant_id, quotation_id);
CREATE INDEX idx_customer_conversation_booking
    ON customer_conversations (tenant_id, booking_id);

DELETE FROM conversation_assignments
WHERE assignment_role = 'COLLABORATOR';

CREATE TABLE ownership_history (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    previous_owner_id UUID,
    owner_id UUID,
    actor_id UUID NOT NULL,
    reason VARCHAR(80) NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_id UUID NOT NULL,
    lead_id UUID,
    quotation_id UUID,
    booking_id UUID,
    conversation_id UUID,
    customer_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_ownership_previous_owner_tenant
        FOREIGN KEY (previous_owner_id, tenant_id)
        REFERENCES tenant_users(id, tenant_id),
    CONSTRAINT fk_ownership_owner_tenant
        FOREIGN KEY (owner_id, tenant_id)
        REFERENCES tenant_users(id, tenant_id),
    CONSTRAINT fk_ownership_actor_tenant
        FOREIGN KEY (actor_id, tenant_id)
        REFERENCES tenant_users(id, tenant_id)
);

CREATE INDEX idx_ownership_history_tenant_time
    ON ownership_history (tenant_id, occurred_at DESC);
CREATE INDEX idx_ownership_history_lead
    ON ownership_history (tenant_id, lead_id, occurred_at DESC);
CREATE INDEX idx_ownership_history_quotation
    ON ownership_history (tenant_id, quotation_id, occurred_at DESC);
CREATE INDEX idx_ownership_history_booking
    ON ownership_history (tenant_id, booking_id, occurred_at DESC);
CREATE INDEX idx_ownership_history_conversation
    ON ownership_history (tenant_id, conversation_id, occurred_at DESC);

DELETE FROM tenant_role_permissions permission
USING tenant_roles role
WHERE permission.role_id = role.id
  AND role.template_code <> 'OWNER'
  AND permission.permission IN ('LEAD_ASSIGN', 'CONVERSATION_ASSIGN');
