-- Bounded operational metadata survives conversation deletion, with no prompt or reply.
ALTER TABLE ai_quota_requests ADD COLUMN input_tokens BIGINT;
ALTER TABLE ai_quota_requests ADD COLUMN output_tokens BIGINT;
ALTER TABLE ai_quota_requests ADD COLUMN terminal_state VARCHAR(20);
ALTER TABLE ai_quota_requests ADD COLUMN failure_code VARCHAR(40);
ALTER TABLE ai_quota_requests ADD COLUMN completed_at TIMESTAMP(6);
ALTER TABLE ai_quota_requests ADD COLUMN duration_ms BIGINT;
CREATE INDEX idx_ai_requests_created ON ai_quota_requests(created_at, owner_id);
