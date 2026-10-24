ALTER TABLE ai_sessions ADD COLUMN outline_only BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE ai_long_plans (
    session_id VARCHAR(36) PRIMARY KEY,
    coverage_text VARCHAR(1000) NOT NULL,
    outline_json MEDIUMTEXT NOT NULL,
    batches_json MEDIUMTEXT NOT NULL,
    completed_json VARCHAR(1000) NOT NULL,
    submissions INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (session_id) REFERENCES ai_sessions(id) ON DELETE CASCADE
);
CREATE TABLE ai_long_jobs (
    job_id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    batch_no INT NOT NULL,
    FOREIGN KEY (job_id) REFERENCES ai_jobs(id) ON DELETE CASCADE,
    FOREIGN KEY (session_id) REFERENCES ai_long_plans(session_id) ON DELETE CASCADE
);
CREATE TABLE ai_collaborations (
    local_ref VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    source_version BIGINT NOT NULL,
    source_json MEDIUMTEXT NOT NULL,
    instructions_text VARCHAR(2000) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    accepted_hash VARCHAR(64),
    accepted_version BIGINT,
    FOREIGN KEY (session_id) REFERENCES ai_sessions(id) ON DELETE CASCADE
);
