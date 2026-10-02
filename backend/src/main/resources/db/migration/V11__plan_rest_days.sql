-- ISO weekday bits: Monday = bit 0, Sunday = bit 6. No implicit weekends.
-- This changes labels only; existing tasks and progress are untouched.
alter table plans add column rest_days_mask integer not null default 0;
