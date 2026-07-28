INSERT INTO tenant_role_permissions (role_id, permission)
SELECT role.id, 'AUDIT_READ'
FROM tenant_roles role
WHERE role.template_code IN ('OWNER', 'MANAGER')
ON CONFLICT (role_id, permission) DO NOTHING;
