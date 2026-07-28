ALTER TABLE bookings
    ADD CONSTRAINT uk_booking_id_tenant UNIQUE (id, tenant_id);

CREATE TABLE driver_assignments (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    booking_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    vehicle_id UUID NOT NULL,
    pickup_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    dropoff_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    pickup_location VARCHAR(500) NOT NULL,
    dropoff_location VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_assignment_booking_tenant
        FOREIGN KEY (booking_id, tenant_id)
        REFERENCES bookings (id, tenant_id),
    CONSTRAINT fk_assignment_driver_tenant
        FOREIGN KEY (driver_id, tenant_id)
        REFERENCES drivers (id, tenant_id),
    CONSTRAINT fk_assignment_vehicle_tenant
        FOREIGN KEY (vehicle_id, tenant_id)
        REFERENCES vehicles (id, tenant_id),
    CONSTRAINT uk_assignment_tenant_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT ck_assignment_times CHECK (pickup_at < dropoff_at),
    CONSTRAINT ck_assignment_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED'))
);

CREATE UNIQUE INDEX uk_assignment_active_booking
    ON driver_assignments (tenant_id, booking_id)
    WHERE active = TRUE AND status IN ('PENDING', 'CONFIRMED');
CREATE INDEX idx_assignment_tenant_pickup
    ON driver_assignments (tenant_id, pickup_at, status);
CREATE INDEX idx_assignment_tenant_driver
    ON driver_assignments (tenant_id, driver_id, pickup_at);
CREATE INDEX idx_assignment_tenant_vehicle
    ON driver_assignments (tenant_id, vehicle_id, pickup_at);

ALTER TABLE driver_assignments
    ADD CONSTRAINT ex_assignment_driver_time
        EXCLUDE USING gist (
            driver_id WITH =,
            tstzrange(pickup_at, dropoff_at, '[)') WITH &&
        )
        WHERE (active = TRUE AND status IN ('PENDING', 'CONFIRMED')),
    ADD CONSTRAINT ex_assignment_vehicle_time
        EXCLUDE USING gist (
            vehicle_id WITH =,
            tstzrange(pickup_at, dropoff_at, '[)') WITH &&
        )
        WHERE (active = TRUE AND status IN ('PENDING', 'CONFIRMED'));

CREATE TABLE driver_assignment_history (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    assignment_id UUID NOT NULL REFERENCES driver_assignments(id),
    action VARCHAR(30) NOT NULL,
    previous_driver_id UUID,
    driver_id UUID NOT NULL,
    previous_vehicle_id UUID,
    vehicle_id UUID NOT NULL,
    previous_status VARCHAR(30),
    status VARCHAR(30) NOT NULL,
    reason VARCHAR(500),
    changed_by UUID,
    CONSTRAINT ck_assignment_history_action
        CHECK (action IN ('ASSIGNED', 'REASSIGNED', 'CONFIRMED', 'CANCELLED')),
    CONSTRAINT ck_assignment_history_previous_status
        CHECK (previous_status IS NULL OR previous_status IN ('PENDING', 'CONFIRMED', 'CANCELLED')),
    CONSTRAINT ck_assignment_history_status
        CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED'))
);

CREATE INDEX idx_assignment_history_tenant_assignment_created
    ON driver_assignment_history (tenant_id, assignment_id, created_at DESC);

CREATE TRIGGER trg_driver_assignment_history_immutable
    BEFORE UPDATE OR DELETE ON driver_assignment_history
    FOR EACH ROW
    EXECUTE FUNCTION reject_driver_status_history_mutation();

CREATE TABLE assignment_notification_outbox (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    assignment_id UUID NOT NULL REFERENCES driver_assignments(id),
    audience VARCHAR(30) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    delivered_at TIMESTAMP(6) WITH TIME ZONE,
    last_error VARCHAR(500),
    CONSTRAINT ck_assignment_notification_audience
        CHECK (audience IN ('CUSTOMER', 'OPERATIONS')),
    CONSTRAINT ck_assignment_notification_event
        CHECK (event_type IN ('ASSIGNED', 'REASSIGNED', 'CONFIRMED', 'CANCELLED')),
    CONSTRAINT ck_assignment_notification_status
        CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
    CONSTRAINT ck_assignment_notification_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_assignment_notification_delivery
    ON assignment_notification_outbox (status, next_attempt_at, created_at);
