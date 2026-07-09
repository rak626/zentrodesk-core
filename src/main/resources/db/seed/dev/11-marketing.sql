set search_path to zentro_desk;

insert into campaigns (
    id, created_at, updated_at, tenant_id, name, type, template_id, scheduled_at, status, active
) values
    ('b0000000-0000-0000-0000-000000000001', current_timestamp, current_timestamp, '10000000-0000-0000-0000-000000000001', 'Summer Sikkim Follow Up', 'WHATSAPP', 'a0000000-0000-0000-0000-000000000001', current_timestamp + interval '7 days', 'SCHEDULED', true),
    ('b0000000-0000-0000-0000-000000000002', current_timestamp, current_timestamp, '10000000-0000-0000-0000-000000000001', 'Quotation Nudge', 'EMAIL', 'a0000000-0000-0000-0000-000000000002', current_timestamp + interval '2 days', 'DRAFT', true)
on conflict (id) do update set
    updated_at = excluded.updated_at,
    tenant_id = excluded.tenant_id,
    name = excluded.name,
    type = excluded.type,
    template_id = excluded.template_id,
    scheduled_at = excluded.scheduled_at,
    status = excluded.status,
    active = excluded.active;
