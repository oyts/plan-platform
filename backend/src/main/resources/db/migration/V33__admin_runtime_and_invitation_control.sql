CREATE TABLE ai_runtime_policy (
 id INTEGER PRIMARY KEY,
 version BIGINT NOT NULL DEFAULT 0,
 settings_json TEXT,
 updated_at TIMESTAMP(6)
);
INSERT INTO ai_runtime_policy(id,version) VALUES (1,0);
CREATE TABLE ai_runtime_audit (
 id VARCHAR(36) PRIMARY KEY,
 actor_id VARCHAR(36) NOT NULL,
 before_json TEXT NOT NULL,
 after_json TEXT NOT NULL,
 restored_from VARCHAR(36),
 created_at TIMESTAMP(6) NOT NULL,
 FOREIGN KEY(actor_id) REFERENCES users(id)
);
CREATE INDEX idx_ai_runtime_audit_date ON ai_runtime_audit(created_at);
ALTER TABLE ai_jobs ADD COLUMN runtime_input_chars INTEGER;
ALTER TABLE ai_jobs ADD COLUMN runtime_output_tokens INTEGER;
ALTER TABLE ai_jobs ADD COLUMN runtime_total_output_tokens INTEGER;
ALTER TABLE ai_jobs ADD COLUMN runtime_timeout_seconds INTEGER;
ALTER TABLE ai_jobs ADD COLUMN runtime_version BIGINT;
CREATE TABLE admin_invitation_control (id INTEGER PRIMARY KEY);
INSERT INTO admin_invitation_control(id) VALUES (1);
CREATE TABLE admin_invitation_operations (
 actor_id VARCHAR(36) NOT NULL,
 operation_key VARCHAR(100) NOT NULL,
 fingerprint VARCHAR(64) NOT NULL,
 invitation_id VARCHAR(36) NOT NULL,
 delivery_status VARCHAR(20) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 PRIMARY KEY(actor_id,operation_key),
 FOREIGN KEY(actor_id) REFERENCES users(id),
 FOREIGN KEY(invitation_id) REFERENCES invitations(id)
);
