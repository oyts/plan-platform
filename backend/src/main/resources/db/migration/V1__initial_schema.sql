create table users (
  id varchar(36) primary key,
  email varchar(190) not null unique,
  password_hash varchar(100) not null,
  display_name varchar(80) not null,
  role varchar(30) not null,
  enabled boolean not null default true,
  version bigint not null default 0,
  created_at timestamp(6) not null,
  updated_at timestamp(6) not null
);

create table refresh_tokens (
  id varchar(36) primary key,
  user_id varchar(36) not null,
  token_hash varchar(64) not null unique,
  expires_at timestamp(6) not null,
  revoked_at timestamp(6),
  created_at timestamp(6) not null,
  constraint fk_refresh_user foreign key (user_id) references users(id) on delete cascade
);

create table plans (
  id varchar(36) primary key,
  owner_id varchar(36) not null,
  name varchar(120) not null,
  description varchar(1000) not null default '',
  start_date date not null,
  end_date date not null,
  archived boolean not null default false,
  version bigint not null default 0,
  created_at timestamp(6) not null,
  updated_at timestamp(6) not null,
  constraint fk_plan_owner foreign key (owner_id) references users(id) on delete cascade
);
create index idx_plans_owner on plans(owner_id, archived, start_date);

create table plan_tags (
  id varchar(36) primary key,
  plan_id varchar(36) not null,
  name varchar(60) not null,
  color varchar(20) not null,
  overall_tag boolean not null default false,
  version bigint not null default 0,
  created_at timestamp(6) not null,
  updated_at timestamp(6) not null,
  constraint fk_tag_plan foreign key (plan_id) references plans(id) on delete cascade,
  constraint uk_tag_name unique(plan_id, name)
);

create table plan_tasks (
  id varchar(36) primary key,
  plan_id varchar(36) not null,
  tag_id varchar(36),
  task_date date not null,
  start_time time,
  end_time time,
  planned_minutes integer not null,
  category varchar(40) not null,
  title varchar(180) not null,
  description varchar(2000) not null default '',
  acceptance_json text not null,
  resource_url varchar(1000) not null default '',
  required_task boolean not null default true,
  sort_order integer not null default 0,
  version bigint not null default 0,
  created_at timestamp(6) not null,
  updated_at timestamp(6) not null,
  constraint fk_task_plan foreign key (plan_id) references plans(id) on delete cascade,
  constraint fk_task_tag foreign key (tag_id) references plan_tags(id) on delete set null
);
create index idx_tasks_plan_date on plan_tasks(plan_id, task_date, sort_order);

create table task_progress (
  id varchar(36) primary key,
  task_id varchar(36) not null unique,
  status varchar(30) not null,
  accumulated_seconds bigint not null default 0,
  started_at timestamp(6),
  completed_at timestamp(6),
  acceptance_json text not null,
  note varchar(2000) not null default '',
  defer_to date,
  skip_reason varchar(1000) not null default '',
  version bigint not null default 0,
  created_at timestamp(6) not null,
  updated_at timestamp(6) not null,
  constraint fk_progress_task foreign key (task_id) references plan_tasks(id) on delete cascade
);

create table time_entries (
  id varchar(36) primary key,
  progress_id varchar(36) not null,
  started_at timestamp(6) not null,
  ended_at timestamp(6) not null,
  seconds bigint not null,
  source varchar(20) not null,
  created_at timestamp(6) not null,
  constraint fk_time_progress foreign key (progress_id) references task_progress(id) on delete cascade
);
create index idx_time_progress on time_entries(progress_id, started_at);

create table daily_reflections (
  id varchar(36) primary key,
  plan_id varchar(36) not null,
  reflection_date date not null,
  completed_summary varchar(2000) not null default '',
  unfinished_summary varchar(2000) not null default '',
  blockers varchar(2000) not null default '',
  first_task_tomorrow varchar(1000) not null default '',
  energy integer,
  note varchar(2000) not null default '',
  version bigint not null default 0,
  created_at timestamp(6) not null,
  updated_at timestamp(6) not null,
  constraint fk_reflection_plan foreign key (plan_id) references plans(id) on delete cascade,
  constraint uk_reflection_date unique(plan_id, reflection_date)
);

create table mainlines (
  id varchar(36) primary key,
  owner_id varchar(36) not null,
  name varchar(120) not null,
  description varchar(1000) not null default '',
  color varchar(20) not null,
  archived boolean not null default false,
  version bigint not null default 0,
  created_at timestamp(6) not null,
  updated_at timestamp(6) not null,
  constraint fk_mainline_owner foreign key (owner_id) references users(id) on delete cascade
);

create table mainline_items (
  id varchar(36) primary key,
  mainline_id varchar(36) not null,
  title varchar(180) not null,
  description varchar(2000) not null default '',
  acceptance_json text not null,
  planned_minutes integer not null,
  sort_order integer not null,
  status varchar(30) not null,
  accumulated_seconds bigint not null default 0,
  note varchar(2000) not null default '',
  version bigint not null default 0,
  created_at timestamp(6) not null,
  updated_at timestamp(6) not null,
  constraint fk_item_mainline foreign key (mainline_id) references mainlines(id) on delete cascade,
  constraint uk_mainline_order unique(mainline_id, sort_order)
);

create table legacy_imports (
  id varchar(36) primary key,
  owner_id varchar(36) not null,
  source_version varchar(30) not null,
  payload text not null,
  imported_at timestamp(6) not null,
  constraint fk_legacy_owner foreign key (owner_id) references users(id) on delete cascade
);
