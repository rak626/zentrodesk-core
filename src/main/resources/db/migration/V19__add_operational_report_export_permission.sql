set search_path to zentro_desk;

insert into tenant_role_permissions (role_id, permission)
select role.id, 'REPORT_EXPORT'
from tenant_roles role
where role.template_code in ('OWNER', 'MANAGER', 'ACCOUNTS')
  and not exists (
      select 1
      from tenant_role_permissions permission
      where permission.role_id = role.id
        and permission.permission = 'REPORT_EXPORT'
  );
