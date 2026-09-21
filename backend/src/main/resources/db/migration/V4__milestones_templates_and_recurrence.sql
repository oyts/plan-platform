create table plan_milestones (
  id varchar(36) primary key,
  plan_id varchar(36) not null,
  name varchar(120) not null,
  milestone_date date not null,
  description varchar(1000) not null default '',
  acceptance_json text not null,
  checks_json text not null,
  note varchar(2000) not null default '',
  reviewed_at timestamp(6),
  version bigint not null default 0,
  created_at timestamp(6) not null,
  updated_at timestamp(6) not null,
  constraint fk_milestone_plan foreign key (plan_id) references plans(id) on delete cascade
);
create index idx_milestone_plan_date on plan_milestones(plan_id, milestone_date);

create table task_templates (
  id varchar(36) primary key,
  owner_id varchar(36) not null,
  name varchar(80) not null,
  category varchar(40) not null,
  title varchar(180) not null,
  description varchar(2000) not null default '',
  planned_minutes integer not null,
  acceptance_json text not null,
  resource_url varchar(1000) not null default '',
  required_task boolean not null default true,
  version bigint not null default 0,
  created_at timestamp(6) not null,
  updated_at timestamp(6) not null,
  constraint fk_template_owner foreign key (owner_id) references users(id) on delete cascade,
  constraint uk_template_owner_name unique(owner_id, name)
);
create index idx_template_owner on task_templates(owner_id, created_at);

alter table plan_tasks add column recurrence_id varchar(36);
alter table plan_tasks add column template_id varchar(36);
create index idx_task_recurrence on plan_tasks(plan_id, recurrence_id);
