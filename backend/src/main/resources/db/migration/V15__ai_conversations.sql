CREATE TABLE ai_sessions (
    id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    selected_mode VARCHAR(20),
    recommended_mode VARCHAR(20),
    draft_json TEXT,
    applied_json TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_ai_sessions_owner ON ai_sessions(owner_id, created_at);
CREATE TABLE ai_jobs (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    operation_key VARCHAR(100) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    reservation_id VARCHAR(36) NOT NULL,
    state VARCHAR(20) NOT NULL CHECK (state IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','UNKNOWN')),
    user_message TEXT NOT NULL,
    context_json TEXT,
    reply_text TEXT,
    error_code VARCHAR(40),
    input_tokens BIGINT,
    output_tokens BIGINT,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE (owner_id, operation_key),
    FOREIGN KEY (session_id) REFERENCES ai_sessions(id) ON DELETE CASCADE,
    FOREIGN KEY (reservation_id) REFERENCES ai_quota_requests(id)
);
CREATE INDEX idx_ai_jobs_state ON ai_jobs(state, created_at);
CREATE INDEX idx_ai_jobs_session ON ai_jobs(session_id, created_at);
CREATE INDEX idx_ai_requests_retention ON ai_quota_requests(quota_day);
