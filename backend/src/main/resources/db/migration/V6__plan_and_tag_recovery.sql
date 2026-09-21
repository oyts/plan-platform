alter table plans add column deleted_at timestamp(6);
alter table plan_tags add column deleted_at timestamp(6);
create index idx_plan_deleted on plans(owner_id, deleted_at, archived, start_date);
create index idx_tag_deleted on plan_tags(plan_id, deleted_at, overall_tag);
