ALTER TABLE quotation_items
    ADD COLUMN check_in DATE,
    ADD COLUMN check_out DATE,
    ADD COLUMN starts_at TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN ends_at TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN pickup_location VARCHAR(500),
    ADD COLUMN dropoff_location VARCHAR(500),
    ADD COLUMN capacity_overridden BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_quotation_item_stay_dates
        CHECK (check_in IS NULL OR check_out IS NULL OR check_in < check_out),
    ADD CONSTRAINT ck_quotation_item_service_times
        CHECK (starts_at IS NULL OR ends_at IS NULL OR starts_at < ends_at);

CREATE TABLE guides (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(150) NOT NULL,
    phone VARCHAR(20),
    languages VARCHAR(500),
    base_rate NUMERIC(12, 2) NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_guide_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_guide_base_rate CHECK (base_rate >= 0),
    CONSTRAINT ck_guide_status CHECK (status IN ('AVAILABLE', 'UNAVAILABLE'))
);

CREATE INDEX idx_guide_tenant_status
    ON guides (tenant_id, status, active);

CREATE TABLE quotation_item_resources (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    quotation_item_id UUID NOT NULL REFERENCES quotation_items(id),
    resource_type VARCHAR(30) NOT NULL,
    resource_id UUID NOT NULL,
    active BOOLEAN NOT NULL,
    CONSTRAINT ck_quotation_item_resource_type
        CHECK (resource_type IN ('ROOM', 'VEHICLE', 'GUIDE'))
);

CREATE UNIQUE INDEX uk_quotation_item_active_resource
    ON quotation_item_resources (quotation_item_id, resource_type, resource_id)
    WHERE active = TRUE;

CREATE INDEX idx_quotation_item_resource_lookup
    ON quotation_item_resources (resource_type, resource_id)
    WHERE active = TRUE;

CREATE TABLE guide_assignments (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    booking_id UUID NOT NULL,
    guide_id UUID NOT NULL,
    starts_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    status VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_guide_assignment_booking_tenant
        FOREIGN KEY (booking_id, tenant_id) REFERENCES bookings (id, tenant_id),
    CONSTRAINT fk_guide_assignment_guide_tenant
        FOREIGN KEY (guide_id, tenant_id) REFERENCES guides (id, tenant_id),
    CONSTRAINT ck_guide_assignment_times CHECK (starts_at < ends_at),
    CONSTRAINT ck_guide_assignment_status CHECK (status IN ('RESERVED', 'CANCELLED'))
);

ALTER TABLE guide_assignments
    ADD CONSTRAINT ex_guide_assignment_time
        EXCLUDE USING gist (
            guide_id WITH =,
            tstzrange(starts_at, ends_at, '[)') WITH &&
        )
        WHERE (active = TRUE AND status = 'RESERVED');

CREATE INDEX idx_guide_assignment_tenant_time
    ON guide_assignments (tenant_id, starts_at, ends_at)
    WHERE active = TRUE AND status = 'RESERVED';

DROP INDEX uk_assignment_active_booking;

CREATE INDEX idx_assignment_active_booking
    ON driver_assignments (tenant_id, booking_id)
    WHERE active = TRUE AND status IN ('PENDING', 'CONFIRMED');

CREATE UNIQUE INDEX uk_driver_active_vehicle
    ON drivers (tenant_id, vehicle_id)
    WHERE vehicle_id IS NOT NULL AND active = TRUE;

INSERT INTO tenant_role_permissions (role_id, permission)
SELECT role.id, permission.permission
FROM tenant_roles role
CROSS JOIN (VALUES
    ('GUIDE_READ'),
    ('GUIDE_CREATE'),
    ('GUIDE_UPDATE'),
    ('GUIDE_DELETE')
) permission(permission)
WHERE role.template_code IN ('OWNER', 'MANAGER', 'OPERATIONS')
ON CONFLICT DO NOTHING;

INSERT INTO tenant_role_permissions (role_id, permission)
SELECT role.id, permission.permission
FROM tenant_roles role
CROSS JOIN (VALUES
    ('PROPERTY_READ'),
    ('ROOM_READ'),
    ('RESERVATION_READ'),
    ('DRIVER_READ'),
    ('GUIDE_READ')
) permission(permission)
WHERE role.template_code = 'SALES'
ON CONFLICT DO NOTHING;
