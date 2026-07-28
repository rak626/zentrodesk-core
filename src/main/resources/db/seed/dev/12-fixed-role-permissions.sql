set search_path to zentro_desk;

update tenant_roles
set name = 'Owner',
    description = 'Full tenant administration and operational access',
    template_code = 'OWNER',
    active = true
where id = '10000000-0000-0000-0000-000000000101';

update tenant_roles
set name = 'Sales',
    description = 'Customer, lead, quotation, and booking sales workflows',
    template_code = 'SALES',
    active = true
where id = '10000000-0000-0000-0000-000000000102';

update tenant_roles
set name = 'Operations',
    description = 'Booking allocation, inventory, driver, and task operations',
    template_code = 'OPERATIONS',
    active = true
where id = '10000000-0000-0000-0000-000000000103';

insert into tenant_roles (id, created_at, updated_at, tenant_id, name, description, template_code, active)
select '10000000-0000-0000-0000-000000000104', current_timestamp, current_timestamp,
       '10000000-0000-0000-0000-000000000001', 'Manager',
       'Operational management with read-only team access', 'MANAGER', true
where not exists (
    select 1 from tenant_roles
    where tenant_id = '10000000-0000-0000-0000-000000000001'
      and template_code = 'MANAGER'
);

insert into tenant_roles (id, created_at, updated_at, tenant_id, name, description, template_code, active)
select '10000000-0000-0000-0000-000000000105', current_timestamp, current_timestamp,
       '10000000-0000-0000-0000-000000000001', 'Accounts',
       'Booking payment and financial reporting access', 'ACCOUNTS', true
where not exists (
    select 1 from tenant_roles
    where tenant_id = '10000000-0000-0000-0000-000000000001'
      and template_code = 'ACCOUNTS'
);

delete from tenant_role_permissions permission
using tenant_roles role
where permission.role_id = role.id
  and role.template_code is not null;

with all_permissions(permission) as (values
    ('DASHBOARD_READ'),
    ('CUSTOMER_READ'), ('CUSTOMER_CREATE'), ('CUSTOMER_UPDATE'), ('CUSTOMER_DELETE'),
    ('LEAD_READ'), ('LEAD_CREATE'), ('LEAD_UPDATE'), ('LEAD_DELETE'), ('LEAD_ASSIGN'),
    ('QUOTATION_READ'), ('QUOTATION_CREATE'), ('QUOTATION_UPDATE'),
    ('BOOKING_READ'), ('BOOKING_CREATE'), ('BOOKING_UPDATE'), ('BOOKING_DELETE'),
    ('PAYMENT_READ'), ('PAYMENT_CREATE'), ('PAYMENT_UPDATE'),
    ('PROPERTY_READ'), ('PROPERTY_CREATE'), ('PROPERTY_UPDATE'),
    ('ROOM_READ'), ('ROOM_CREATE'), ('ROOM_UPDATE'),
    ('RESERVATION_READ'), ('RESERVATION_CREATE'), ('RESERVATION_UPDATE'),
    ('DRIVER_READ'), ('DRIVER_CREATE'), ('DRIVER_UPDATE'), ('DRIVER_DELETE'),
    ('TASK_READ'), ('TASK_CREATE'), ('TASK_UPDATE'),
    ('CONVERSATION_READ'), ('CONVERSATION_CREATE'), ('CONVERSATION_UPDATE'),
    ('NOTIFICATION_READ'), ('NOTIFICATION_UPDATE'), ('REPORT_READ'), ('REPORT_EXPORT'),
    ('TEAM_READ'), ('TEAM_CREATE'), ('TEAM_UPDATE'), ('TEAM_DELETE'),
    ('TEAM_ASSIGN_ROLE'), ('TEAM_RESET_PASSWORD'), ('ROLE_READ')
)
insert into tenant_role_permissions (role_id, permission)
select role.id, permission.permission
from tenant_roles role
cross join all_permissions permission
where role.template_code = 'OWNER'
union all
select role.id, permission.permission
from tenant_roles role
cross join all_permissions permission
where role.template_code = 'MANAGER'
  and permission.permission not in (
      'CUSTOMER_DELETE', 'BOOKING_DELETE', 'TEAM_CREATE', 'TEAM_UPDATE', 'TEAM_DELETE',
      'TEAM_ASSIGN_ROLE', 'TEAM_RESET_PASSWORD'
  )
union all
select role.id, permission.permission
from tenant_roles role
cross join all_permissions permission
where role.template_code = 'SALES'
  and permission.permission in (
      'DASHBOARD_READ', 'CUSTOMER_READ', 'CUSTOMER_CREATE', 'CUSTOMER_UPDATE',
      'LEAD_READ', 'LEAD_CREATE', 'LEAD_UPDATE', 'LEAD_ASSIGN',
      'QUOTATION_READ', 'QUOTATION_CREATE', 'QUOTATION_UPDATE',
      'BOOKING_READ', 'BOOKING_CREATE', 'BOOKING_UPDATE', 'PAYMENT_READ',
      'TASK_READ', 'TASK_CREATE', 'TASK_UPDATE',
      'CONVERSATION_READ', 'CONVERSATION_CREATE', 'CONVERSATION_UPDATE',
      'NOTIFICATION_READ', 'NOTIFICATION_UPDATE'
  )
union all
select role.id, permission.permission
from tenant_roles role
cross join all_permissions permission
where role.template_code = 'OPERATIONS'
  and permission.permission in (
      'DASHBOARD_READ', 'CUSTOMER_READ', 'BOOKING_READ', 'BOOKING_UPDATE',
      'PROPERTY_READ', 'PROPERTY_CREATE', 'PROPERTY_UPDATE',
      'ROOM_READ', 'ROOM_CREATE', 'ROOM_UPDATE',
      'RESERVATION_READ', 'RESERVATION_CREATE', 'RESERVATION_UPDATE',
      'DRIVER_READ', 'DRIVER_CREATE', 'DRIVER_UPDATE', 'DRIVER_DELETE',
      'TASK_READ', 'TASK_CREATE', 'TASK_UPDATE', 'NOTIFICATION_READ', 'NOTIFICATION_UPDATE'
  )
union all
select role.id, permission.permission
from tenant_roles role
cross join all_permissions permission
where role.template_code = 'ACCOUNTS'
  and permission.permission in (
      'DASHBOARD_READ', 'CUSTOMER_READ', 'BOOKING_READ', 'PAYMENT_READ',
      'PAYMENT_CREATE', 'PAYMENT_UPDATE', 'NOTIFICATION_READ', 'NOTIFICATION_UPDATE',
      'REPORT_READ', 'REPORT_EXPORT'
  );
