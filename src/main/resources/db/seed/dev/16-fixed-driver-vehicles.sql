set search_path to zentro_desk;

insert into vehicles (
    id,
    created_at,
    updated_at,
    tenant_id,
    registration_number,
    type,
    capacity,
    status,
    active,
    version
) values
    (
        '81000000-0000-0000-0000-000000000001',
        current_timestamp,
        current_timestamp,
        '10000000-0000-0000-0000-000000000001',
        'SK01AB1234',
        'SUV',
        4,
        'AVAILABLE',
        true,
        0
    ),
    (
        '81000000-0000-0000-0000-000000000002',
        current_timestamp,
        current_timestamp,
        '10000000-0000-0000-0000-000000000001',
        'SK02CD5678',
        'Tempo Traveller',
        12,
        'AVAILABLE',
        true,
        0
    )
on conflict (tenant_id, registration_number) do update set
    updated_at = excluded.updated_at,
    type = excluded.type,
    capacity = excluded.capacity,
    status = excluded.status,
    active = excluded.active;

update drivers driver
set vehicle_id = vehicle.id,
    vehicle_number = vehicle.registration_number,
    vehicle_type = vehicle.type,
    updated_at = current_timestamp
from vehicles vehicle
where driver.tenant_id = vehicle.tenant_id
  and driver.vehicle_number = vehicle.registration_number
  and driver.vehicle_id is null;
