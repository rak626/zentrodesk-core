set search_path to zentro_desk;

insert into tenant_users (
    id, created_at, updated_at, tenant_id, role_id, first_name, last_name,
    email, phone, password_hash, last_login_at, active, session_version
)
select identity.id::uuid, current_timestamp, current_timestamp,
       '10000000-0000-0000-0000-000000000001'::uuid, role.id,
       identity.first_name, 'Release', identity.email, identity.phone,
       '$2a$10$p5fbPORpSB9ramt5/MkVt.hZzz963uDRioF4CdQMMtanz57GFtn06',
       null, true, 0
from (values
    ('10000000-0000-0000-0000-000000000206', 'Sales', 'sales@zentrodesk.test', '+919999000106', 'SALES'),
    ('10000000-0000-0000-0000-000000000207', 'Operations', 'operations@zentrodesk.test', '+919999000107', 'OPERATIONS')
) as identity(id, first_name, email, phone, template_code)
join tenant_roles role
  on role.tenant_id = '10000000-0000-0000-0000-000000000001'
 and role.template_code = identity.template_code
on conflict on constraint uk_user_tenant_email do update set
    updated_at = excluded.updated_at,
    role_id = excluded.role_id,
    first_name = excluded.first_name,
    last_name = excluded.last_name,
    phone = excluded.phone,
    password_hash = excluded.password_hash,
    active = excluded.active,
    session_version = excluded.session_version;
