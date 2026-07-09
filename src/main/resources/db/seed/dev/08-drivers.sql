set search_path to zentro_desk;

insert into drivers (
    id, created_at, updated_at, tenant_id, name, phone, license_number, vehicle_number, vehicle_type, status, active
) values
    ('80000000-0000-0000-0000-000000000001', current_timestamp, current_timestamp, '10000000-0000-0000-0000-000000000001', 'Dorjee Tamang', '+919666000001', 'SK-DRV-0001', 'SK01AB1234', 'SUV', 'AVAILABLE', true),
    ('80000000-0000-0000-0000-000000000002', current_timestamp, current_timestamp, '10000000-0000-0000-0000-000000000001', 'Bikash Pradhan', '+919666000002', 'SK-DRV-0002', 'SK02CD5678', 'Tempo Traveller', 'AVAILABLE', true)
on conflict (id) do update set
    updated_at = excluded.updated_at,
    name = excluded.name,
    phone = excluded.phone,
    license_number = excluded.license_number,
    vehicle_number = excluded.vehicle_number,
    vehicle_type = excluded.vehicle_type,
    status = excluded.status,
    active = excluded.active;
