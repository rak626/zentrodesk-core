set search_path to zentro_desk;

insert into templates (
    id, created_at, updated_at, tenant_id, name, type, content, status, active
) values
    ('a0000000-0000-0000-0000-000000000001', current_timestamp, current_timestamp, '10000000-0000-0000-0000-000000000001', 'Booking Confirmation WhatsApp', 'WHATSAPP', 'Hi {{customerName}}, your booking {{bookingNumber}} is confirmed.', 'ACTIVE', true),
    ('a0000000-0000-0000-0000-000000000002', current_timestamp, current_timestamp, '10000000-0000-0000-0000-000000000001', 'Quotation Follow Up Email', 'EMAIL', 'We are following up on quotation {{quotationNumber}}.', 'ACTIVE', true)
on conflict (id) do update set
    updated_at = excluded.updated_at,
    name = excluded.name,
    type = excluded.type,
    content = excluded.content,
    status = excluded.status,
    active = excluded.active;

insert into conversation_messages (
    id, created_at, updated_at, tenant_id, customer_id, phone, direction, message, provider_message_id, status, active
) values
    ('a0000000-0000-0000-0000-000000000101', current_timestamp, current_timestamp, '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', '+919888000001', 'OUTBOUND', 'Your booking BK-DEV-001 is confirmed.', 'wamid.dev.001', 'SENT', true),
    ('a0000000-0000-0000-0000-000000000102', current_timestamp, current_timestamp, '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', '+919888000001', 'INBOUND', 'Thank you, please share hotel details.', 'wamid.dev.002', 'READ', true)
on conflict (id) do update set
    updated_at = excluded.updated_at,
    customer_id = excluded.customer_id,
    phone = excluded.phone,
    direction = excluded.direction,
    message = excluded.message,
    provider_message_id = excluded.provider_message_id,
    status = excluded.status,
    active = excluded.active;

insert into notifications (
    id, created_at, updated_at, user_id, title, message, status, active
) values
    ('a0000000-0000-0000-0000-000000000201', current_timestamp, current_timestamp, '10000000-0000-0000-0000-000000000202', 'New lead assigned', 'Ankit Verma has been assigned to you.', 'UNREAD', true),
    ('a0000000-0000-0000-0000-000000000202', current_timestamp, current_timestamp, '10000000-0000-0000-0000-000000000203', 'Booking confirmed', 'BK-DEV-001 is ready for operations planning.', 'UNREAD', true)
on conflict (id) do update set
    updated_at = excluded.updated_at,
    user_id = excluded.user_id,
    title = excluded.title,
    message = excluded.message,
    status = excluded.status,
    active = excluded.active;
