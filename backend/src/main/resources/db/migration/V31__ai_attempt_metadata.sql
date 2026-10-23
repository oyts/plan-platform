CREATE TABLE ai_job_attempts (
    job_id VARCHAR(36) NOT NULL,
    attempt_no INTEGER NOT NULL CHECK (attempt_no IN (0,1)),
    state VARCHAR(20) NOT NULL,
    reason VARCHAR(40),
    input_tokens BIGINT,
    output_tokens BIGINT,
    input_chars INTEGER,
    output_chars INTEGER,
    started_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6),
    PRIMARY KEY (job_id, attempt_no),
    FOREIGN KEY (job_id) REFERENCES ai_jobs(id) ON DELETE CASCADE
);
ALTER TABLE ai_quota_requests ADD COLUMN token_usage_complete BOOLEAN;
ALTER TABLE ai_sessions ADD COLUMN requirements_text TEXT;
ALTER TABLE ai_jobs ADD COLUMN request_intent VARCHAR(20) NOT NULL DEFAULT 'AUTO';
