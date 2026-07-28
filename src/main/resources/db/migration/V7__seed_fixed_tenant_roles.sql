set search_path to zentro_desk;

alter table tenant_roles add column template_code varchar(30);

with candidates as (
    select id,
           case
               when upper(name) in ('ADMINISTRATOR', 'OWNER') then 'OWNER'
               when upper(name) = 'MANAGER' then 'MANAGER'
               when upper(name) in ('SALES EXECUTIVE', 'SALES') then 'SALES'
               when upper(name) in ('OPERATIONS EXECUTIVE', 'OPERATIONS') then 'OPERATIONS'
               when upper(name) = 'ACCOUNTS' then 'ACCOUNTS'
           end as template_code,
           row_number() over (
               partition by tenant_id,
                   case
                       when upper(name) in ('ADMINISTRATOR', 'OWNER') then 'OWNER'
                       when upper(name) = 'MANAGER' then 'MANAGER'
                       when upper(name) in ('SALES EXECUTIVE', 'SALES') then 'SALES'
                       when upper(name) in ('OPERATIONS EXECUTIVE', 'OPERATIONS') then 'OPERATIONS'
                       when upper(name) = 'ACCOUNTS' then 'ACCOUNTS'
                   end
               order by created_at, id
           ) as candidate_order
    from tenant_roles
    where upper(name) in (
        'ADMINISTRATOR', 'OWNER', 'MANAGER', 'SALES EXECUTIVE', 'SALES',
        'OPERATIONS EXECUTIVE', 'OPERATIONS', 'ACCOUNTS'
    )
)
update tenant_roles role
set template_code = candidate.template_code
from candidates candidate
where role.id = candidate.id
  and candidate.candidate_order = 1;

update tenant_roles
set name = case template_code
        when 'OWNER' then 'Owner'
        when 'MANAGER' then 'Manager'
        when 'SALES' then 'Sales'
        when 'OPERATIONS' then 'Operations'
        when 'ACCOUNTS' then 'Accounts'
    end,
    description = case template_code
        when 'OWNER' then 'Full tenant administration and operational access'
        when 'MANAGER' then 'Operational management with read-only team access'
        when 'SALES' then 'Customer, lead, quotation, and booking sales workflows'
        when 'OPERATIONS' then 'Booking allocation, inventory, driver, and task operations'
        when 'ACCOUNTS' then 'Booking payment and financial reporting access'
    end,
    active = true
where template_code is not null;

insert into tenant_roles (id, created_at, updated_at, tenant_id, name, description, template_code, active)
select gen_random_uuid(), current_timestamp, current_timestamp, tenant.id, template.name, template.description,
       template.code, true
from tenants tenant
cross join (values
    ('OWNER', 'Owner', 'Full tenant administration and operational access'),
    ('MANAGER', 'Manager', 'Operational management with read-only team access'),
    ('SALES', 'Sales', 'Customer, lead, quotation, and booking sales workflows'),
    ('OPERATIONS', 'Operations', 'Booking allocation, inventory, driver, and task operations'),
    ('ACCOUNTS', 'Accounts', 'Booking payment and financial reporting access')
) as template(code, name, description)
where not exists (
    select 1
    from tenant_roles existing
    where existing.tenant_id = tenant.id
      and existing.template_code = template.code
);

alter table tenant_roles add constraint ck_tenant_role_template_code
    check (template_code is null or template_code in ('OWNER', 'MANAGER', 'SALES', 'OPERATIONS', 'ACCOUNTS'));
alter table tenant_roles add constraint uk_tenant_role_template unique (tenant_id, template_code);

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
    ('NOTIFICATION_READ'), ('NOTIFICATION_UPDATE'), ('REPORT_READ'),
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
      'PAYMENT_CREATE', 'PAYMENT_UPDATE', 'NOTIFICATION_READ', 'NOTIFICATION_UPDATE', 'REPORT_READ'
  );

create index idx_tenant_role_template on tenant_roles(template_code);
