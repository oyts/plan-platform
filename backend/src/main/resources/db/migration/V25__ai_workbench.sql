CREATE TABLE ai_saved_drafts (
    id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    name VARCHAR(120) NOT NULL,
    content_json MEDIUMTEXT NOT NULL,
    source_session_id VARCHAR(36),
    adjustment_json MEDIUMTEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_ai_saved_owner ON ai_saved_drafts(owner_id, updated_at, id);
