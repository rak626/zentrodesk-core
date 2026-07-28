ALTER TABLE event_publication
    ALTER COLUMN serialized_event TYPE TEXT;
ALTER TABLE event_publication_archive
    ALTER COLUMN serialized_event TYPE TEXT;

ALTER TABLE customers
    ADD COLUMN normalized_phone VARCHAR(32),
    ADD COLUMN normalized_email VARCHAR(150),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE customers
SET normalized_phone = NULLIF(regexp_replace(phone, '[^0-9]+', '', 'g'), ''),
    normalized_email = NULLIF(lower(trim(email)), '');

ALTER TABLE customers DROP CONSTRAINT uk_customer_tenant_phone;

CREATE UNIQUE INDEX uk_customer_tenant_normalized_phone
    ON customers (tenant_id, normalized_phone)
    WHERE normalized_phone IS NOT NULL AND active = TRUE;
CREATE INDEX idx_customer_tenant_normalized_email
    ON customers (tenant_id, normalized_email);

ALTER TABLE leads
    ADD COLUMN customer_id UUID REFERENCES customers(id),
    ADD COLUMN normalized_phone VARCHAR(32),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_lead_adults CHECK (adults IS NULL OR adults >= 0),
    ADD CONSTRAINT ck_lead_children CHECK (children IS NULL OR children >= 0),
    ADD CONSTRAINT ck_lead_budget CHECK (budget IS NULL OR budget >= 0);

UPDATE leads
SET normalized_phone = NULLIF(regexp_replace(phone, '[^0-9]+', '', 'g'), '');

CREATE INDEX idx_lead_tenant_customer ON leads (tenant_id, customer_id);
CREATE INDEX idx_lead_tenant_status_assignee_date
    ON leads (tenant_id, status, assigned_user_id, travel_date);
CREATE INDEX idx_lead_tenant_normalized_phone
    ON leads (tenant_id, normalized_phone);

CREATE TABLE lead_follow_ups (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    lead_id UUID NOT NULL REFERENCES leads(id),
    owner_id UUID NOT NULL REFERENCES tenant_users(id),
    due_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    priority VARCHAR(20) NOT NULL,
    notes VARCHAR(1000),
    outcome VARCHAR(1000),
    completed_at TIMESTAMP(6) WITH TIME ZONE,
    reminder_state VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_follow_up_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT ck_follow_up_reminder_state CHECK (reminder_state IN ('PENDING', 'SENT', 'DISABLED'))
);

CREATE INDEX idx_follow_up_tenant_lead ON lead_follow_ups (tenant_id, lead_id);
CREATE INDEX idx_follow_up_tenant_owner_due
    ON lead_follow_ups (tenant_id, owner_id, due_at);

CREATE TABLE lead_activities (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    lead_id UUID NOT NULL REFERENCES leads(id),
    actor_id UUID REFERENCES tenant_users(id),
    activity_type VARCHAR(40) NOT NULL,
    summary VARCHAR(500) NOT NULL
);

CREATE INDEX idx_lead_activity_tenant_lead_created
    ON lead_activities (tenant_id, lead_id, created_at DESC);

ALTER TABLE quotations
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_quotation_subtotal CHECK (subtotal IS NULL OR subtotal >= 0),
    ADD CONSTRAINT ck_quotation_discount CHECK (discount IS NULL OR discount >= 0),
    ADD CONSTRAINT ck_quotation_tax CHECK (tax IS NULL OR tax >= 0),
    ADD CONSTRAINT ck_quotation_total CHECK (total >= 0);

ALTER TABLE quotation_items
    ADD COLUMN position INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_quotation_item_position CHECK (position >= 0),
    ADD CONSTRAINT ck_quotation_item_quantity CHECK (quantity > 0),
    ADD CONSTRAINT ck_quotation_item_unit_price CHECK (unit_price >= 0),
    ADD CONSTRAINT ck_quotation_item_amount CHECK (amount >= 0);

WITH ranked_items AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY quotation_id
               ORDER BY created_at, id
           ) - 1 AS item_position
    FROM quotation_items
)
UPDATE quotation_items item
SET position = ranked_items.item_position
FROM ranked_items
WHERE item.id = ranked_items.id;

CREATE UNIQUE INDEX uk_quotation_item_position
    ON quotation_items (quotation_id, position)
    WHERE active = TRUE;

CREATE TABLE document_counters (
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    document_type VARCHAR(30) NOT NULL,
    next_value BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, document_type),
    CONSTRAINT ck_document_counter_next CHECK (next_value > 0)
);

ALTER TABLE bookings DROP CONSTRAINT uk_booking_number;

ALTER TABLE bookings
    ADD COLUMN agreed_value NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN idempotency_key VARCHAR(100),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT uk_booking_tenant_number UNIQUE (tenant_id, booking_number),
    ADD CONSTRAINT uk_booking_tenant_idempotency UNIQUE (tenant_id, idempotency_key),
    ADD CONSTRAINT ck_booking_agreed_value CHECK (agreed_value >= 0);

CREATE INDEX idx_booking_tenant_quotation ON bookings (tenant_id, quotation_id);
CREATE UNIQUE INDEX uk_booking_tenant_quotation_conversion
    ON bookings (tenant_id, quotation_id)
    WHERE quotation_id IS NOT NULL AND active = TRUE;

ALTER TABLE booking_guests
    ADD CONSTRAINT ck_booking_guest_gender
        CHECK (gender IS NULL OR gender IN ('FEMALE', 'MALE', 'OTHER', 'UNSPECIFIED'));

ALTER TABLE payments
    ADD COLUMN tenant_id UUID,
    ADD COLUMN idempotency_key VARCHAR(100),
    ADD COLUMN reversal_of_payment_id UUID REFERENCES payments(id),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE payments payment
SET tenant_id = booking.tenant_id
FROM bookings booking
WHERE booking.id = payment.booking_id;

ALTER TABLE payments
    ALTER COLUMN tenant_id SET NOT NULL,
    ADD CONSTRAINT uk_payment_tenant_idempotency UNIQUE (tenant_id, idempotency_key),
    ADD CONSTRAINT ck_payment_amount CHECK (amount > 0);

CREATE UNIQUE INDEX uk_payment_tenant_reference
    ON payments (tenant_id, transaction_reference)
    WHERE transaction_reference IS NOT NULL AND active = TRUE;
CREATE INDEX idx_payment_tenant_booking ON payments (tenant_id, booking_id);

CREATE FUNCTION derive_payment_tenant()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.tenant_id IS NULL THEN
        SELECT tenant_id INTO NEW.tenant_id
        FROM zentro_desk.bookings
        WHERE id = NEW.booking_id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_payment_tenant
    BEFORE INSERT OR UPDATE OF booking_id, tenant_id ON payments
    FOR EACH ROW
    EXECUTE FUNCTION derive_payment_tenant();

ALTER TABLE properties
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE rooms
    ADD COLUMN amenities_summary VARCHAR(500),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_room_capacity CHECK (capacity > 0),
    ADD CONSTRAINT ck_room_base_price CHECK (base_price >= 0);

ALTER TABLE tasks
    ADD COLUMN source_type VARCHAR(40),
    ADD COLUMN source_id UUID;

CREATE UNIQUE INDEX uk_task_tenant_source
    ON tasks (tenant_id, source_type, source_id)
    WHERE source_id IS NOT NULL AND active = TRUE;
