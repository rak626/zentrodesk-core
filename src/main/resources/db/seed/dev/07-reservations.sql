set search_path to zentro_desk;

insert into reservations (
    id, created_at, updated_at, booking_id, property_id, room_id, check_in, check_out, status, active
) values
    ('70000000-0000-0000-0000-000000000001', current_timestamp, current_timestamp, '60000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000002', current_date + 30, current_date + 33, 'RESERVED', true),
    ('70000000-0000-0000-0000-000000000002', current_timestamp, current_timestamp, '60000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000003', current_date + 33, current_date + 35, 'RESERVED', true)
on conflict (id) do update set
    updated_at = excluded.updated_at,
    booking_id = excluded.booking_id,
    property_id = excluded.property_id,
    room_id = excluded.room_id,
    check_in = excluded.check_in,
    check_out = excluded.check_out,
    status = excluded.status,
    active = excluded.active;
