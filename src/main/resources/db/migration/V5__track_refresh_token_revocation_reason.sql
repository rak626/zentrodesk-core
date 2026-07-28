set search_path to zentro_desk;

alter table account_refresh_tokens add column revoked_reason varchar(30);

update account_refresh_tokens
set revoked_reason = 'LEGACY'
where revoked;
