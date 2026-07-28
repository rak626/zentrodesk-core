set search_path to zentro_desk;

insert into customers (
    id, created_at, updated_at, tenant_id, name, phone, normalized_phone,
    email, normalized_email, address, city, state, country, notes, active
) values
    ('20000000-0000-0000-0000-000000000001', current_timestamp, current_timestamp, '10000000-0000-0000-0000-000000000001', 'Rahul Sharma', '+919888000001', '919888000001', 'rahul.sharma@example.test', 'rahul.sharma@example.test', 'Salt Lake', 'Kolkata', 'West Bengal', 'India', 'Interested in family tours.', true),
    ('20000000-0000-0000-0000-000000000002', current_timestamp, current_timestamp, '10000000-0000-0000-0000-000000000001', 'Priya Mehta', '+919888000002', '919888000002', 'priya.mehta@example.test', 'priya.mehta@example.test', 'Bandra West', 'Mumbai', 'Maharashtra', 'India', 'Prefers premium hotels.', true)
on conflict (tenant_id, normalized_phone)
    where normalized_phone is not null and active = true
do update set
    updated_at = excluded.updated_at,
    name = excluded.name,
    phone = excluded.phone,
    email = excluded.email,
    normalized_email = excluded.normalized_email,
    address = excluded.address,
    city = excluded.city,
    state = excluded.state,
    country = excluded.country,
    notes = excluded.notes,
    active = excluded.active;
