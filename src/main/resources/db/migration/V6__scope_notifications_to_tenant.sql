set search_path to zentro_desk;

alter table notifications add column tenant_id uuid;

update notifications notification
set tenant_id = tenant_user.tenant_id
from tenant_users tenant_user
where tenant_user.id = notification.user_id;

alter table notifications alter column tenant_id set not null;
alter table tenant_users add constraint uk_tenant_user_id_tenant unique (id, tenant_id);
alter table notifications add constraint fk_notification_tenant_user
    foreign key (user_id, tenant_id) references tenant_users(id, tenant_id);
create index idx_notification_tenant_user on notifications(tenant_id, user_id);
