set search_path to zentro_desk;

create or replace function set_notification_tenant_from_user()
returns trigger
language plpgsql
as $$
declare
    expected_tenant_id uuid;
begin
    select tenant_id
    into expected_tenant_id
    from zentro_desk.tenant_users
    where id = new.user_id;

    if expected_tenant_id is null then
        raise exception 'Notification user does not exist';
    end if;

    if new.tenant_id is null then
        new.tenant_id = expected_tenant_id;
    elsif new.tenant_id <> expected_tenant_id then
        raise exception 'Notification tenant does not match user tenant';
    end if;

    return new;
end;
$$;
