ALTER TABLE templates
    ADD COLUMN style_content TEXT,
    ADD COLUMN whatsapp_template_name VARCHAR(512),
    ADD COLUMN whatsapp_template_language VARCHAR(10),
    ADD COLUMN template_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN published_at TIMESTAMPTZ;

ALTER TABLE quotations
    ADD CONSTRAINT uk_quotation_id_tenant UNIQUE (id, tenant_id);

ALTER TABLE customers
    ADD CONSTRAINT uk_customer_id_tenant UNIQUE (id, tenant_id);

ALTER TABLE templates
    ADD CONSTRAINT uk_template_id_tenant UNIQUE (id, tenant_id);

CREATE UNIQUE INDEX uk_quotation_pdf_template_active
    ON templates (tenant_id, type)
    WHERE type = 'QUOTATION_PDF' AND status = 'ACTIVE' AND active = TRUE;

CREATE TABLE quotation_documents (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    quotation_id UUID NOT NULL,
    customer_id UUID,
    document_version INTEGER NOT NULL,
    template_id UUID,
    template_version INTEGER NOT NULL,
    template_name VARCHAR(100) NOT NULL,
    html_snapshot TEXT NOT NULL,
    style_snapshot TEXT,
    model_snapshot TEXT NOT NULL,
    file_name VARCHAR(180) NOT NULL,
    content BYTEA,
    content_sha256 VARCHAR(64),
    content_length BIGINT,
    generation_status VARCHAR(20) NOT NULL,
    generation_attempts INTEGER NOT NULL DEFAULT 0,
    next_generation_attempt_at TIMESTAMPTZ NOT NULL,
    generated_at TIMESTAMPTZ,
    send_requested BOOLEAN NOT NULL DEFAULT FALSE,
    delivery_status VARCHAR(20) NOT NULL,
    recipient_name VARCHAR(150),
    recipient_phone VARCHAR(32),
    whatsapp_template_name VARCHAR(512),
    whatsapp_template_language VARCHAR(10),
    provider_message_id VARCHAR(100),
    delivered_at TIMESTAMPTZ,
    last_error VARCHAR(500),
    CONSTRAINT uk_quotation_document_version
        UNIQUE (quotation_id, document_version),
    CONSTRAINT uk_quotation_document_id_tenant
        UNIQUE (id, tenant_id),
    CONSTRAINT fk_quotation_document_quotation_tenant
        FOREIGN KEY (quotation_id, tenant_id)
        REFERENCES quotations(id, tenant_id),
    CONSTRAINT fk_quotation_document_customer_tenant
        FOREIGN KEY (customer_id, tenant_id)
        REFERENCES customers(id, tenant_id),
    CONSTRAINT fk_quotation_document_template_tenant
        FOREIGN KEY (template_id, tenant_id)
        REFERENCES templates(id, tenant_id),
    CONSTRAINT ck_quotation_document_generation_status
        CHECK (generation_status IN ('PENDING', 'GENERATED', 'FAILED', 'SUPERSEDED')),
    CONSTRAINT ck_quotation_document_delivery_status
        CHECK (delivery_status IN ('NOT_REQUESTED', 'PENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_quotation_document_generation_attempts
        CHECK (generation_attempts >= 0)
);

CREATE INDEX idx_quotation_document_processing
    ON quotation_documents (generation_status, next_generation_attempt_at, created_at);

CREATE INDEX idx_quotation_document_lookup
    ON quotation_documents (tenant_id, quotation_id, document_version DESC);

ALTER TABLE conversation_messages
    ADD COLUMN document_id UUID,
    ADD COLUMN document_file_name VARCHAR(180),
    ADD COLUMN template_name VARCHAR(512),
    ADD COLUMN template_language VARCHAR(10),
    ADD CONSTRAINT fk_conversation_message_document_tenant
        FOREIGN KEY (document_id, tenant_id)
        REFERENCES quotation_documents(id, tenant_id);

CREATE UNIQUE INDEX uk_conversation_message_document
    ON conversation_messages (document_id)
    WHERE document_id IS NOT NULL;
