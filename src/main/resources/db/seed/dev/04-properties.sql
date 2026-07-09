set search_path to zentro_desk;

insert into properties (
    id, created_at, updated_at, tenant_id, name, type, address, city, state, country, phone, email, contact_person, status, active
) values
    ('40000000-0000-0000-0000-000000000001', current_timestamp, current_timestamp, '10000000-0000-0000-0000-000000000001', 'Zentro Hill View Resort', 'RESORT', 'Tadong Road', 'Gangtok', 'Sikkim', 'India', '+913592000001', 'hillview@example.test', 'Pema Bhutia', 'ACTIVE', true),
    ('40000000-0000-0000-0000-000000000002', current_timestamp, current_timestamp, '10000000-0000-0000-0000-000000000001', 'Pelling Valley Homestay', 'HOMESTAY', 'Upper Pelling', 'Pelling', 'Sikkim', 'India', '+913595000001', 'valley@example.test', 'Karma Lepcha', 'ACTIVE', true)
on conflict (id) do update set
    updated_at = excluded.updated_at,
    name = excluded.name,
    type = excluded.type,
    address = excluded.address,
    city = excluded.city,
    state = excluded.state,
    country = excluded.country,
    phone = excluded.phone,
    email = excluded.email,
    contact_person = excluded.contact_person,
    status = excluded.status,
    active = excluded.active;
