set search_path to zentro_desk;

insert into platform_users (
    id, created_at, updated_at, first_name, last_name, email, password_hash, role, last_login_at, enabled
) values
    ('00000000-0000-0000-0000-000000000001', current_timestamp, current_timestamp, 'Platform', 'Admin', 'platform-admin@zentrodesk.test', '$2a$10$p5fbPORpSB9ramt5/MkVt.hZzz963uDRioF4CdQMMtanz57GFtn06', 'SUPER_ADMIN', null, true)
on conflict (email) do update set
    updated_at = excluded.updated_at,
    first_name = excluded.first_name,
    last_name = excluded.last_name,
    password_hash = excluded.password_hash,
    role = excluded.role,
    enabled = excluded.enabled;

insert into tenants (
    id, created_at, updated_at, name, code, email, phone, address, city, state, country, timezone, currency, active
) values
    ('10000000-0000-0000-0000-000000000001', current_timestamp, current_timestamp, 'Zentro Holidays', 'ZENTRO', 'hello@zentrodesk.test', '+919999000001', 'MG Road', 'Gangtok', 'Sikkim', 'India', 'Asia/Kolkata', 'INR', true)
on conflict (code) do update set
    updated_at = excluded.updated_at,
    name = excluded.name,
    email = excluded.email,
    phone = excluded.phone,
    address = excluded.address,
    city = excluded.city,
    state = excluded.state,
    country = excluded.country,
    timezone = excluded.timezone,
    currency = excluded.currency,
    active = excluded.active;

insert into tenant_roles (
    id, created_at, updated_at, tenant_id, name, description, active
) values
    ('10000000-0000-0000-0000-000000000101', current_timestamp, current_timestamp, '10000000-0000-0000-0000-000000000001', 'Administrator', 'Full access to tenant operations', true),
    ('10000000-0000-0000-0000-000000000102', current_timestamp, current_timestamp, '10000000-0000-0000-0000-000000000001', 'Sales Executive', 'Lead, customer, and quotation management', true),
    ('10000000-0000-0000-0000-000000000103', current_timestamp, current_timestamp, '10000000-0000-0000-0000-000000000001', 'Operations Executive', 'Booking, reservation, driver, and task management', true)
on conflict (id) do update set
    updated_at = excluded.updated_at,
    name = excluded.name,
    description = excluded.description,
    active = excluded.active;

insert into tenant_users (
    id, created_at, updated_at, tenant_id, role_id, first_name, last_name, email, phone, password_hash, last_login_at, active
) values
    ('10000000-0000-0000-0000-000000000201', current_timestamp, current_timestamp, '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000101', 'Admin', 'User', 'admin@zentrodesk.test', '+919999000101', '$2a$10$p5fbPORpSB9ramt5/MkVt.hZzz963uDRioF4CdQMMtanz57GFtn06', null, true),
    ('10000000-0000-0000-0000-000000000202', current_timestamp, current_timestamp, '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000102', 'Asha', 'Rai', 'asha@zentrodesk.test', '+919999000102', '$2a$10$bhysKlWUgozAzzRkK9r.J.yYGJ.3OngFlzLJ1o27HiWkpVnQVZbCa', null, true),
    ('10000000-0000-0000-0000-000000000203', current_timestamp, current_timestamp, '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000103', 'Nima', 'Lepcha', 'nima@zentrodesk.test', '+919999000103', '$2a$10$bhysKlWUgozAzzRkK9r.J.yYGJ.3OngFlzLJ1o27HiWkpVnQVZbCa', null, true)
on conflict on constraint uk_user_tenant_email do update set
    updated_at = excluded.updated_at,
    role_id = excluded.role_id,
    first_name = excluded.first_name,
    last_name = excluded.last_name,
    phone = excluded.phone,
    password_hash = excluded.password_hash,
    active = excluded.active;
