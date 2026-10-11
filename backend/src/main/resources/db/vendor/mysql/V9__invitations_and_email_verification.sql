alter table users add column email_verified_at timestamp(6);

create table invitations (
    id varchar(36) primary key,
    email varchar(190) not null,
    token_hash varchar(64) not null unique,
    expires_at timestamp(6) not null,
    accepted_at timestamp(6),
    revoked_at timestamp(6),
    created_by varchar(36) not null,
    version bigint not null default 0,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint fk_invitation_creator foreign key (created_by) references users(id) on delete cascade
);
create index idx_invitation_email_active on invitations(email, accepted_at, revoked_at, expires_at);

create table email_verification_tokens (
    id varchar(36) primary key,
    user_id varchar(36) not null,
    token_hash varchar(64) not null unique,
    expires_at timestamp(6) not null,
    used_at timestamp(6),
    version bigint not null default 0,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint fk_email_verification_user foreign key (user_id) references users(id) on delete cascade
);
create index idx_email_verification_active on email_verification_tokens(user_id, used_at, expires_at);
