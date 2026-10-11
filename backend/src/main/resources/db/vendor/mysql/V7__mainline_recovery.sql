alter table mainlines add column deleted_at timestamp(6);
alter table mainline_items add column deleted_at timestamp(6);

create index idx_mainlines_owner_deleted on mainlines(owner_id, deleted_at);
create index idx_mainline_items_deleted on mainline_items(mainline_id, deleted_at);
