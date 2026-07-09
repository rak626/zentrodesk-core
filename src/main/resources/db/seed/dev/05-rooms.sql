set search_path to zentro_desk;

insert into rooms (
    id, created_at, updated_at, property_id, name, type, capacity, base_price, status, active
) values
    ('50000000-0000-0000-0000-000000000001', current_timestamp, current_timestamp, '40000000-0000-0000-0000-000000000001', 'HV-101', 'DELUXE', 3, 6500.00, 'AVAILABLE', true),
    ('50000000-0000-0000-0000-000000000002', current_timestamp, current_timestamp, '40000000-0000-0000-0000-000000000001', 'HV-201', 'FAMILY', 4, 8200.00, 'AVAILABLE', true),
    ('50000000-0000-0000-0000-000000000003', current_timestamp, current_timestamp, '40000000-0000-0000-0000-000000000002', 'PV-01', 'STANDARD', 2, 3600.00, 'AVAILABLE', true)
on conflict on constraint uk_room_property_name do update set
    updated_at = excluded.updated_at,
    type = excluded.type,
    capacity = excluded.capacity,
    base_price = excluded.base_price,
    status = excluded.status,
    active = excluded.active;
