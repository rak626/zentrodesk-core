set search_path to zentro_desk;

alter table roles rename to tenant_roles;
alter table role_permissions rename to tenant_role_permissions;
alter table users rename to tenant_users;

create table platform_users (
    id uuid primary key,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    first_name varchar(100) not null,
    last_name varchar(100),
    email varchar(150) not null unique,
    password_hash varchar(255) not null,
    role varchar(50) not null,
    last_login_at timestamp(6) with time zone,
    enabled boolean not null
);

create table account_refresh_tokens (
    id uuid primary key,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    account_type varchar(20) not null,
    account_id uuid not null,
    token varchar(512) not null unique,
    expires_at timestamp(6) with time zone not null,
    revoked boolean not null,
    revoked_at timestamp(6) with time zone
);

create index idx_platform_user_email on platform_users(email);
create index idx_platform_user_role on platform_users(role);
create index idx_account_refresh_token_account on account_refresh_tokens(account_type, account_id);
create index idx_account_refresh_token_token on account_refresh_tokens(token);
