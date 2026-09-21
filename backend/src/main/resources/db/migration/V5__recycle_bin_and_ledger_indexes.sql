alter table plan_tasks add column deleted_at timestamp(6);
alter table plan_milestones add column deleted_at timestamp(6);
alter table task_templates add column deleted_at timestamp(6);
create index idx_task_deleted on plan_tasks(plan_id, deleted_at, task_date);
create index idx_milestone_deleted on plan_milestones(plan_id, deleted_at, milestone_date);
create index idx_template_deleted on task_templates(owner_id, deleted_at, created_at);
create index idx_time_started on time_entries(started_at);
