SET search_path TO zentro_desk;

INSERT INTO tenant_role_permissions (role_id, permission)
SELECT id, 'QUOTATION_DELETE'
FROM tenant_roles
WHERE template_code IN ('OWNER', 'MANAGER', 'SALES')
ON CONFLICT DO NOTHING;
