ALTER TABLE task_templates ADD COLUMN restored_original_name VARCHAR(80);
CREATE TABLE personal_restore_batches (
    id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    operation_key VARCHAR(36) NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    report_json TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    UNIQUE (owner_id, operation_key),
    UNIQUE (owner_id, checksum),
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_personal_restore_owner ON personal_restore_batches(owner_id, created_at, id);
-- Active weekly reviews are unique per owner/week. Restore into explicit read-only
-- copies, never overwrite an existing review or silently move its original week.
CREATE TABLE restored_weekly_reviews (
    id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    batch_id VARCHAR(36) NOT NULL,
    week_start DATE NOT NULL,
    wins VARCHAR(4000) NOT NULL,
    blockers VARCHAR(4000) NOT NULL,
    next_focus VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (batch_id) REFERENCES personal_restore_batches(id)
);
CREATE INDEX idx_restored_weekly_owner ON restored_weekly_reviews(owner_id, week_start, id);
