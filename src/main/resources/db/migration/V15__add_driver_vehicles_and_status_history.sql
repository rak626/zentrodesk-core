CREATE TABLE vehicles (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    registration_number VARCHAR(100) NOT NULL,
    type VARCHAR(100) NOT NULL,
    make VARCHAR(100),
    model VARCHAR(100),
    capacity INTEGER,
    status VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_vehicle_tenant_registration UNIQUE (tenant_id, registration_number),
    CONSTRAINT uk_vehicle_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_vehicle_capacity CHECK (capacity IS NULL OR capacity > 0),
    CONSTRAINT ck_vehicle_status CHECK (status IN ('AVAILABLE', 'MAINTENANCE', 'UNAVAILABLE'))
);

CREATE INDEX idx_vehicle_tenant_status
    ON vehicles (tenant_id, status, active);

INSERT INTO vehicles (
    id,
    created_at,
    updated_at,
    tenant_id,
    registration_number,
    type,
    status,
    active,
    version
)
SELECT gen_random_uuid(),
       driver.created_at,
       driver.updated_at,
       driver.tenant_id,
       driver.vehicle_number,
       COALESCE(NULLIF(driver.vehicle_type, ''), 'VEHICLE'),
       'AVAILABLE',
       driver.active,
       0
FROM drivers driver
WHERE driver.vehicle_number IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM vehicles vehicle
      WHERE vehicle.tenant_id = driver.tenant_id
        AND vehicle.registration_number = driver.vehicle_number
  );

ALTER TABLE drivers
    ADD COLUMN vehicle_id UUID,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT uk_driver_id_tenant UNIQUE (id, tenant_id),
    ADD CONSTRAINT fk_driver_vehicle_tenant
        FOREIGN KEY (vehicle_id, tenant_id)
        REFERENCES vehicles (id, tenant_id),
    ADD CONSTRAINT ck_driver_status CHECK (status IN ('AVAILABLE', 'ON_TRIP', 'UNAVAILABLE'));

UPDATE drivers driver
SET vehicle_id = vehicle.id
FROM vehicles vehicle
WHERE vehicle.tenant_id = driver.tenant_id
  AND vehicle.registration_number = driver.vehicle_number;

CREATE INDEX idx_driver_tenant_status_active
    ON drivers (tenant_id, status, active);
CREATE INDEX idx_driver_tenant_vehicle
    ON drivers (tenant_id, vehicle_id);

CREATE TABLE driver_status_history (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    driver_id UUID NOT NULL,
    previous_status VARCHAR(30),
    status VARCHAR(30) NOT NULL,
    reason VARCHAR(500),
    changed_by UUID,
    CONSTRAINT fk_driver_history_driver_tenant
        FOREIGN KEY (driver_id, tenant_id)
        REFERENCES drivers (id, tenant_id),
    CONSTRAINT ck_driver_history_previous_status
        CHECK (previous_status IS NULL OR previous_status IN ('AVAILABLE', 'ON_TRIP', 'UNAVAILABLE')),
    CONSTRAINT ck_driver_history_status
        CHECK (status IN ('AVAILABLE', 'ON_TRIP', 'UNAVAILABLE'))
);

CREATE INDEX idx_driver_history_tenant_driver_created
    ON driver_status_history (tenant_id, driver_id, created_at DESC);

INSERT INTO driver_status_history (
    id,
    created_at,
    updated_at,
    tenant_id,
    driver_id,
    previous_status,
    status,
    reason,
    changed_by
)
SELECT gen_random_uuid(),
       current_timestamp,
       current_timestamp,
       driver.tenant_id,
       driver.id,
       NULL,
       driver.status,
       'Migrated current status',
       NULL
FROM drivers driver;

CREATE FUNCTION reject_driver_status_history_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'driver status history is immutable';
END;
$$;

CREATE TRIGGER trg_driver_status_history_immutable
    BEFORE UPDATE OR DELETE ON driver_status_history
    FOR EACH ROW
    EXECUTE FUNCTION reject_driver_status_history_mutation();
