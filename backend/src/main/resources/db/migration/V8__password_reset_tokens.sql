create table password_reset_tokens (
    id varchar(36) primary key,
    user_id varchar(36) not null,
    token_hash varchar(64) not null unique,
    expires_at timestamp(6) not null,
    used_at timestamp(6),
    version bigint not null default 0,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint fk_password_reset_user foreign key (user_id) references users(id) on delete cascade
);
create index idx_password_reset_user_active on password_reset_tokens(user_id, used_at, expires_at);
