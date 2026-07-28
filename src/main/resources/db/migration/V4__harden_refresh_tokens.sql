set search_path to zentro_desk;

alter table tenant_users add column session_version integer not null default 0;
alter table platform_users add column session_version integer not null default 0;

alter table account_refresh_tokens rename column token to token_hash;
alter table account_refresh_tokens add column family_id uuid;
alter table account_refresh_tokens add column parent_token_id uuid;
alter table account_refresh_tokens add column reuse_detected_at timestamp(6) with time zone;

update account_refresh_tokens
set token_hash = encode(sha256(convert_to(token_hash, 'UTF8')), 'hex'),
    family_id = id;

alter table account_refresh_tokens alter column family_id set not null;
alter table account_refresh_tokens alter column token_hash type varchar(64);

drop index idx_account_refresh_token_token;
create index idx_account_refresh_token_hash on account_refresh_tokens(token_hash);
create index idx_account_refresh_token_family on account_refresh_tokens(family_id);

update refresh_tokens
set token = encode(sha256(convert_to(token, 'UTF8')), 'hex');
