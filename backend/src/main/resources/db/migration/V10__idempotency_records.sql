create table idempotency_records (
    id varchar(36) primary key,
    owner_id varchar(36) not null,
    operation_key varchar(100) not null,
    request_method varchar(10) not null,
    request_path varchar(500) not null,
    request_fingerprint varchar(64) not null,
    response_status integer,
    response_content_type varchar(200),
    response_body text,
    completed boolean not null default false,
    expires_at timestamp(6) not null,
    version bigint not null default 0,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint fk_idempotency_owner foreign key (owner_id) references users(id) on delete cascade,
    constraint uk_idempotency_owner_key unique(owner_id, operation_key)
);
create index idx_idempotency_expiry on idempotency_records(expires_at);
