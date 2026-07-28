set search_path to zentro_desk;

alter table notifications
    add column category varchar(40) default 'SYSTEM',
    add column deep_link varchar(300),
    add column event_key varchar(180) default gen_random_uuid()::text,
    add column source_type varchar(50),
    add column source_id uuid;

update notifications
set category = 'SYSTEM',
    event_key = 'LEGACY:' || id
where category is null
   or event_key is null;

alter table notifications
    alter column category set not null,
    alter column event_key set not null,
    add constraint uk_notification_recipient_event
        unique (tenant_id, user_id, event_key),
    add constraint ck_notification_deep_link
        check (
            deep_link is null
            or deep_link ~ '^/(dashboard|leads|bookings|availability|drivers|reports)([/#?].*)?$'
        );

create index idx_notification_recipient_unread
    on notifications (tenant_id, user_id, status, active, created_at desc);
