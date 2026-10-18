ALTER TABLE users ADD COLUMN auth_generation BIGINT NOT NULL DEFAULT 0;
CREATE TABLE member_governance_audit (
    id VARCHAR(36) PRIMARY KEY,
    actor_id VARCHAR(36) NOT NULL,
    target_id VARCHAR(36) NOT NULL,
    action VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (actor_id) REFERENCES users(id),
    FOREIGN KEY (target_id) REFERENCES users(id)
);
CREATE INDEX idx_governance_audit_time ON member_governance_audit(created_at,id);
CREATE TABLE operations_alerts (
    code VARCHAR(40) PRIMARY KEY,
    incident_id VARCHAR(36) NOT NULL,
    active BOOLEAN NOT NULL,
    first_seen TIMESTAMP(6) NOT NULL,
    last_seen TIMESTAMP(6) NOT NULL,
    resolved_at TIMESTAMP(6)
);
CREATE TABLE operations_policy (
    id INTEGER PRIMARY KEY,
    keep_newest INTEGER NOT NULL,
    retention_days INTEGER NOT NULL,
    backup_max_hours INTEGER NOT NULL,
    disk_min_percent INTEGER NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
INSERT INTO operations_policy(id,keep_newest,retention_days,backup_max_hours,disk_min_percent,version) VALUES (1,7,30,26,15,0);
CREATE TABLE operations_policy_audit (
    id VARCHAR(36) PRIMARY KEY,
    actor_id VARCHAR(36) NOT NULL,
    previous_version BIGINT NOT NULL,
    new_version BIGINT NOT NULL,
    before_json VARCHAR(1000) NOT NULL,
    after_json VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY(actor_id) REFERENCES users(id)
);
