alter table legacy_imports add column checksum varchar(64);
alter table legacy_imports add column status varchar(20) not null default 'SNAPSHOT';
alter table legacy_imports add column report_json text;
alter table legacy_imports add column completed_at timestamp(6);
alter table legacy_imports add column rolled_back_at timestamp(6);

update legacy_imports
set checksum = concat('legacy-', replace(id, '-', ''))
where checksum is null;

alter table legacy_imports add constraint uk_legacy_owner_checksum unique(owner_id, checksum);
create index idx_legacy_owner_status on legacy_imports(owner_id, status, imported_at);
