set search_path to zentro_desk;

insert into tasks (
    id, created_at, updated_at, tenant_id, user_id, title, description, due_at, status, active
) values
    ('90000000-0000-0000-0000-000000000001', current_timestamp, current_timestamp, '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000202', 'Follow up with Sneha Kapoor', 'Send revised Darjeeling hotel options.', current_timestamp + interval '1 day', 'PENDING', true),
    ('90000000-0000-0000-0000-000000000002', current_timestamp, current_timestamp, '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000203', 'Confirm vehicle for BK-DEV-001', 'Assign available SUV before travel starts.', current_timestamp + interval '3 days', 'PENDING', true)
on conflict (id) do update set
    updated_at = excluded.updated_at,
    tenant_id = excluded.tenant_id,
    user_id = excluded.user_id,
    title = excluded.title,
    description = excluded.description,
    due_at = excluded.due_at,
    status = excluded.status,
    active = excluded.active;
