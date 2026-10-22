CREATE TABLE email_change_requests (
    id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    old_email VARCHAR(190) NOT NULL,
    new_email VARCHAR(190) NOT NULL,
    password_fingerprint VARCHAR(64) NOT NULL,
    auth_generation BIGINT NOT NULL,
    old_token_hash VARCHAR(64) NOT NULL UNIQUE,
    new_token_hash VARCHAR(64) NOT NULL UNIQUE,
    old_verified_at TIMESTAMP(6),
    new_verified_at TIMESTAMP(6),
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    consumed_at TIMESTAMP(6),
    cancelled_at TIMESTAMP(6),
    CONSTRAINT fk_email_change_owner FOREIGN KEY (owner_id) REFERENCES users(id)
);
CREATE INDEX idx_email_change_owner_created ON email_change_requests(owner_id, created_at, id);
