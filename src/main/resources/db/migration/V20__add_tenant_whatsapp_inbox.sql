CREATE TABLE whatsapp_configurations (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    waba_id VARCHAR(100) NOT NULL,
    business_id VARCHAR(100) NOT NULL,
    phone_number_id VARCHAR(100) NOT NULL,
    display_phone_number VARCHAR(32) NOT NULL,
    verified_name VARCHAR(150),
    connection_method VARCHAR(30) NOT NULL,
    access_token_ciphertext TEXT,
    access_token_expires_at TIMESTAMPTZ,
    funding_status VARCHAR(20) NOT NULL,
    token_healthy BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL,
    last_validated_at TIMESTAMPTZ,
    last_healthy_at TIMESTAMPTZ,
    last_error VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_whatsapp_configuration_tenant UNIQUE (tenant_id),
    CONSTRAINT uk_whatsapp_configuration_waba UNIQUE (waba_id),
    CONSTRAINT uk_whatsapp_configuration_phone_number UNIQUE (phone_number_id),
    CONSTRAINT ck_whatsapp_configuration_method
        CHECK (connection_method IN ('EMBEDDED_SIGNUP', 'MANUAL')),
    CONSTRAINT ck_whatsapp_configuration_funding
        CHECK (funding_status IN ('FUNDED', 'UNFUNDED', 'UNKNOWN')),
    CONSTRAINT ck_whatsapp_configuration_status
        CHECK (status IN ('CONNECTED', 'ERROR', 'DISCONNECTED'))
);

CREATE TABLE customer_conversations (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    customer_id UUID REFERENCES customers(id),
    phone VARCHAR(32) NOT NULL,
    normalized_phone VARCHAR(32) NOT NULL,
    display_name VARCHAR(150),
    status VARCHAR(20) NOT NULL,
    last_message_at TIMESTAMPTZ,
    last_inbound_at TIMESTAMPTZ,
    unread_count INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_customer_conversation_tenant_phone UNIQUE (tenant_id, normalized_phone),
    CONSTRAINT uk_customer_conversation_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_customer_conversation_status
        CHECK (status IN ('OPEN', 'PENDING', 'CLOSED')),
    CONSTRAINT ck_customer_conversation_unread_count CHECK (unread_count >= 0)
);

INSERT INTO customer_conversations (
    id,
    created_at,
    updated_at,
    tenant_id,
    phone,
    normalized_phone,
    status,
    last_message_at,
    last_inbound_at,
    unread_count,
    active,
    version
)
SELECT
    gen_random_uuid(),
    min(created_at),
    max(updated_at),
    tenant_id,
    max(phone),
    regexp_replace(phone, '[^0-9]', '', 'g'),
    'OPEN',
    max(created_at),
    max(created_at) FILTER (WHERE direction = 'INBOUND'),
    count(*) FILTER (WHERE direction = 'INBOUND' AND status <> 'READ'),
    TRUE,
    0
FROM conversation_messages
GROUP BY tenant_id, regexp_replace(phone, '[^0-9]', '', 'g');

ALTER TABLE conversation_messages
    ADD COLUMN conversation_id UUID,
    ADD COLUMN sent_by_user_id UUID REFERENCES tenant_users(id),
    ADD COLUMN business_phone_number_id VARCHAR(100),
    ADD COLUMN message_type VARCHAR(20) NOT NULL DEFAULT 'TEXT';

UPDATE conversation_messages message
SET conversation_id = conversation.id
FROM customer_conversations conversation
WHERE conversation.tenant_id = message.tenant_id
  AND conversation.normalized_phone = regexp_replace(message.phone, '[^0-9]', '', 'g');

ALTER TABLE conversation_messages
    ALTER COLUMN conversation_id SET NOT NULL,
    ADD CONSTRAINT fk_conversation_message_conversation_tenant
        FOREIGN KEY (conversation_id, tenant_id)
        REFERENCES customer_conversations(id, tenant_id),
    ADD CONSTRAINT ck_conversation_message_type
        CHECK (message_type IN ('TEXT', 'TEMPLATE', 'IMAGE', 'DOCUMENT', 'UNKNOWN'));

CREATE INDEX idx_conversation_message_thread
    ON conversation_messages (conversation_id, created_at);
CREATE UNIQUE INDEX uk_conversation_message_provider
    ON conversation_messages (tenant_id, provider_message_id)
    WHERE provider_message_id IS NOT NULL;
CREATE INDEX idx_customer_conversation_inbox
    ON customer_conversations (tenant_id, status, last_message_at DESC);

CREATE TABLE conversation_assignments (
    conversation_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    assignment_role VARCHAR(20) NOT NULL,
    assigned_by_user_id UUID NOT NULL REFERENCES tenant_users(id),
    assigned_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (conversation_id, user_id),
    CONSTRAINT fk_conversation_assignment_conversation_tenant
        FOREIGN KEY (conversation_id, tenant_id)
        REFERENCES customer_conversations(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_conversation_assignment_user_tenant
        FOREIGN KEY (user_id, tenant_id)
        REFERENCES tenant_users(id, tenant_id),
    CONSTRAINT ck_conversation_assignment_role
        CHECK (assignment_role IN ('PRIMARY', 'COLLABORATOR'))
);

CREATE UNIQUE INDEX uk_conversation_primary_assignment
    ON conversation_assignments (conversation_id)
    WHERE assignment_role = 'PRIMARY';
CREATE INDEX idx_conversation_assignment_user
    ON conversation_assignments (tenant_id, user_id, assigned_at DESC);

CREATE TABLE whatsapp_message_outbox (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    conversation_id UUID NOT NULL,
    message_id UUID NOT NULL REFERENCES conversation_messages(id),
    sender_phone_number_id VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    delivered_at TIMESTAMPTZ,
    last_error VARCHAR(500),
    CONSTRAINT fk_whatsapp_outbox_conversation_tenant
        FOREIGN KEY (conversation_id, tenant_id)
        REFERENCES customer_conversations(id, tenant_id),
    CONSTRAINT uk_whatsapp_outbox_message UNIQUE (message_id),
    CONSTRAINT ck_whatsapp_outbox_status
        CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_whatsapp_outbox_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_whatsapp_outbox_delivery
    ON whatsapp_message_outbox (status, next_attempt_at, created_at);

CREATE TABLE whatsapp_webhook_inbox (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    tenant_id UUID REFERENCES tenants(id),
    phone_number_id VARCHAR(100),
    payload_hash VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    last_error VARCHAR(500),
    CONSTRAINT uk_whatsapp_webhook_payload_hash UNIQUE (payload_hash),
    CONSTRAINT ck_whatsapp_webhook_status
        CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED')),
    CONSTRAINT ck_whatsapp_webhook_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_whatsapp_webhook_processing
    ON whatsapp_webhook_inbox (status, next_attempt_at, created_at);

INSERT INTO tenant_role_permissions (role_id, permission)
SELECT role.id, permission.permission
FROM tenant_roles role
CROSS JOIN (
    VALUES
        ('CONVERSATION_READ'),
        ('CONVERSATION_CREATE'),
        ('CONVERSATION_UPDATE')
) permission(permission)
WHERE role.template_code IN ('OPERATIONS', 'ACCOUNTS')
ON CONFLICT DO NOTHING;

INSERT INTO tenant_role_permissions (role_id, permission)
SELECT id, 'CONVERSATION_ASSIGN'
FROM tenant_roles
WHERE template_code IN ('OWNER', 'MANAGER')
ON CONFLICT DO NOTHING;

INSERT INTO tenant_role_permissions (role_id, permission)
SELECT id, 'WHATSAPP_CONFIGURE'
FROM tenant_roles
WHERE template_code = 'OWNER'
ON CONFLICT DO NOTHING;
