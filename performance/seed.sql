\set ON_ERROR_STOP on
set search_path to zentro_desk;

-- Isolated performance environments only. Requires the dev seed so canonical
-- fixed-role permissions can be copied without maintaining a second matrix.
do $$
begin
    if not exists (
        select 1 from tenant_roles
        where tenant_id = '10000000-0000-0000-0000-000000000001'
          and template_code = 'OWNER'
    ) then
        raise exception 'Run the application with the dev seed before loading performance data';
    end if;
end;
$$;

insert into tenants (
    id, created_at, updated_at, name, code, email, timezone, currency, active,
    subscription_status, plan_code, seat_limit
)
select md5('perf-tenant-' || tenant_no)::uuid,
       current_timestamp,
       current_timestamp,
       'Performance Tenant ' || tenant_no,
       'PERF' || lpad(tenant_no::text, 4, '0'),
       'tenant' || tenant_no || '@perf.test',
       'Asia/Kolkata',
       'INR',
       true,
       'ACTIVE',
       'PERFORMANCE',
       25
from generate_series(1, 500) tenant_no
on conflict (code) do nothing;

insert into tenant_roles (
    id, created_at, updated_at, tenant_id, name, description, template_code, active
)
select md5('perf-role-' || tenant_no || '-' || template.code)::uuid,
       current_timestamp,
       current_timestamp,
       md5('perf-tenant-' || tenant_no)::uuid,
       template.name,
       'Synthetic performance role',
       template.code,
       true
from generate_series(1, 500) tenant_no
cross join (values
    ('OWNER', 'Owner'),
    ('MANAGER', 'Manager'),
    ('SALES', 'Sales'),
    ('OPERATIONS', 'Operations'),
    ('ACCOUNTS', 'Accounts')
) template(code, name)
on conflict (tenant_id, template_code) do nothing;

insert into tenant_role_permissions (role_id, permission)
select md5('perf-role-' || tenant_no || '-' || canonical_role.template_code)::uuid,
       permission.permission
from generate_series(1, 500) tenant_no
join tenant_roles canonical_role
  on canonical_role.tenant_id = '10000000-0000-0000-0000-000000000001'
join tenant_role_permissions permission on permission.role_id = canonical_role.id
where canonical_role.template_code is not null
on conflict do nothing;

insert into tenant_users (
    id, created_at, updated_at, tenant_id, role_id, first_name, last_name,
    email, password_hash, active, session_version
)
select md5('perf-user-' || tenant_no || '-' || user_no)::uuid,
       current_timestamp,
       current_timestamp,
       md5('perf-tenant-' || tenant_no)::uuid,
       md5(
           'perf-role-' || tenant_no || '-' ||
           (array['OWNER', 'MANAGER', 'SALES', 'OPERATIONS', 'ACCOUNTS'])[((user_no - 1) % 5) + 1]
       )::uuid,
       'User ' || user_no,
       'Tenant ' || tenant_no,
       lower(
           (array['owner', 'manager', 'sales', 'operations', 'accounts'])[((user_no - 1) % 5) + 1] ||
           user_no || '@perf' || tenant_no || '.test'
       ),
       '$2a$10$p5fbPORpSB9ramt5/MkVt.hZzz963uDRioF4CdQMMtanz57GFtn06',
       true,
       0
from generate_series(1, 500) tenant_no
cross join generate_series(1, 20) user_no
on conflict on constraint uk_user_tenant_email do nothing;

insert into customers (
    id, created_at, updated_at, tenant_id, name, phone, normalized_phone,
    email, normalized_email, city, country, notes, active, version
)
select md5('perf-customer-' || tenant_no || '-' || customer_no)::uuid,
       current_timestamp - ((customer_no % 30) || ' days')::interval,
       current_timestamp,
       md5('perf-tenant-' || tenant_no)::uuid,
       'Customer ' || customer_no,
       '+91' || lpad(tenant_no::text, 4, '0') || lpad(customer_no::text, 6, '0'),
       '91' || lpad(tenant_no::text, 4, '0') || lpad(customer_no::text, 6, '0'),
       'customer' || customer_no || '@perf' || tenant_no || '.test',
       'customer' || customer_no || '@perf' || tenant_no || '.test',
       'Gangtok',
       'India',
       'Synthetic performance data',
       true,
       0
from generate_series(1, 500) tenant_no
cross join generate_series(1, 40) customer_no
on conflict do nothing;

insert into leads (
    id, created_at, updated_at, tenant_id, assigned_user_id, customer_id,
    name, phone, normalized_phone, email, source, destination, travel_date,
    adults, children, budget, status, remarks, active, version
)
select md5('perf-lead-' || tenant_no || '-' || lead_no)::uuid,
       current_timestamp - ((lead_no % 60) || ' days')::interval,
       current_timestamp,
       md5('perf-tenant-' || tenant_no)::uuid,
       md5('perf-user-' || tenant_no || '-' || (((lead_no - 1) % 20) + 1))::uuid,
       md5('perf-customer-' || tenant_no || '-' || (((lead_no - 1) % 40) + 1))::uuid,
       'Lead ' || lead_no,
       '+92' || lpad(tenant_no::text, 4, '0') || lpad(lead_no::text, 6, '0'),
       '92' || lpad(tenant_no::text, 4, '0') || lpad(lead_no::text, 6, '0'),
       'lead' || lead_no || '@perf' || tenant_no || '.test',
       'PERFORMANCE',
       'Sikkim',
       current_date + (lead_no % 90),
       2,
       lead_no % 3,
       25000 + lead_no,
       (array['NEW', 'CONTACTED', 'QUALIFIED', 'QUOTATION_SENT', 'NEGOTIATING', 'WON'])[((lead_no - 1) % 6) + 1],
       'Synthetic performance data',
       true,
       0
from generate_series(1, 500) tenant_no
cross join generate_series(1, 60) lead_no
on conflict do nothing;

insert into bookings (
    id, created_at, updated_at, tenant_id, customer_id, booking_number,
    travel_start, travel_end, status, notes, active, agreed_value, version
)
select md5('perf-booking-' || tenant_no || '-' || booking_no)::uuid,
       current_timestamp - ((booking_no % 30) || ' days')::interval,
       current_timestamp,
       md5('perf-tenant-' || tenant_no)::uuid,
       md5('perf-customer-' || tenant_no || '-' || (((booking_no - 1) % 40) + 1))::uuid,
       'PERF-' || lpad(tenant_no::text, 4, '0') || '-' || lpad(booking_no::text, 4, '0'),
       current_date + booking_no,
       current_date + booking_no + 3,
       (array['CONFIRMED', 'IN_PROGRESS', 'COMPLETED'])[((booking_no - 1) % 3) + 1],
       'Synthetic performance data',
       true,
       50000,
       0
from generate_series(1, 500) tenant_no
cross join generate_series(1, 20) booking_no
on conflict do nothing;

insert into payments (
    id, created_at, updated_at, booking_id, tenant_id, amount, method, status,
    transaction_reference, paid_at, remarks, active, version
)
select md5('perf-payment-' || tenant_no || '-' || booking_no)::uuid,
       current_timestamp,
       current_timestamp,
       md5('perf-booking-' || tenant_no || '-' || booking_no)::uuid,
       md5('perf-tenant-' || tenant_no)::uuid,
       25000,
       'UPI',
       'PARTIAL',
       'PERF-PAY-' || tenant_no || '-' || booking_no,
       current_timestamp,
       'Synthetic performance data',
       true,
       0
from generate_series(1, 500) tenant_no
cross join generate_series(1, 20) booking_no
on conflict do nothing;

analyze tenants;
analyze tenant_users;
analyze customers;
analyze leads;
analyze bookings;
analyze payments;
