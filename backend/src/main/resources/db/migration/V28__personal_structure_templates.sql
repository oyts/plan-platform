CREATE TABLE personal_templates (
 id VARCHAR(36) PRIMARY KEY, owner_id VARCHAR(36) NOT NULL,
 kind VARCHAR(8) NOT NULL, name VARCHAR(120) NOT NULL, description VARCHAR(1000) NOT NULL,
 category VARCHAR(60) NOT NULL, favorite BOOLEAN NOT NULL DEFAULT FALSE,
 blueprint_json MEDIUMTEXT NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 FOREIGN KEY(owner_id) REFERENCES users(id)
);
CREATE INDEX idx_personal_templates_owner ON personal_templates(owner_id,updated_at);
CREATE TABLE personal_template_operations (
 id VARCHAR(36) PRIMARY KEY, owner_id VARCHAR(36) NOT NULL, operation_key VARCHAR(36) NOT NULL,
 fingerprint VARCHAR(64) NOT NULL, result_json TEXT NOT NULL, created_at TIMESTAMP(6) NOT NULL,
 FOREIGN KEY(owner_id) REFERENCES users(id), UNIQUE(owner_id,operation_key)
);
