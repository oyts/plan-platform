alter table mainlines add column if not exists deleted_at timestamp(6);
alter table mainline_items add column if not exists deleted_at timestamp(6);

create index if not exists idx_mainlines_owner_deleted on mainlines(owner_id, deleted_at);
create index if not exists idx_mainline_items_deleted on mainline_items(mainline_id, deleted_at);
