CREATE TABLE ai_quota_policy (
    id INTEGER PRIMARY KEY,
    daily_limit INTEGER NOT NULL CHECK (daily_limit BETWEEN 0 AND 10000),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL
);
INSERT INTO ai_quota_policy(id, daily_limit, version, updated_at) VALUES (1, 0, 0, CURRENT_TIMESTAMP);
CREATE TABLE ai_user_quota (
    owner_id VARCHAR(36) PRIMARY KEY,
    daily_limit INTEGER CHECK (daily_limit BETWEEN 0 AND 10000),
    paused BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE TABLE ai_quota_requests (
    id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    operation_key VARCHAR(100) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    quota_day DATE NOT NULL,
    state VARCHAR(20) NOT NULL CHECK (state IN ('RESERVED','SUCCEEDED','RELEASED','UNKNOWN')),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE (owner_id, operation_key),
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_ai_quota_day ON ai_quota_requests(owner_id, quota_day, state);
CREATE TABLE ai_quota_audit (
    id VARCHAR(36) PRIMARY KEY,
    actor_id VARCHAR(36) NOT NULL,
    target_id VARCHAR(36),
    change_type VARCHAR(20) NOT NULL,
    before_limit INTEGER,
    after_limit INTEGER,
    before_paused BOOLEAN NOT NULL,
    after_paused BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (actor_id) REFERENCES users(id),
    FOREIGN KEY (target_id) REFERENCES users(id)
);
CREATE INDEX idx_ai_audit_created ON ai_quota_audit(created_at);
