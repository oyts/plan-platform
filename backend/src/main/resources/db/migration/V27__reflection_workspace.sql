CREATE TABLE daily_reviews (
 id VARCHAR(36) PRIMARY KEY, owner_id VARCHAR(36) NOT NULL, review_date DATE NOT NULL,
 wins VARCHAR(4000) NOT NULL DEFAULT '', blockers VARCHAR(4000) NOT NULL DEFAULT '', next_focus VARCHAR(4000) NOT NULL DEFAULT '',
 energy VARCHAR(16) NOT NULL DEFAULT 'UNSPECIFIED', burden VARCHAR(16) NOT NULL DEFAULT 'UNSPECIFIED',
 reviewed BOOLEAN NOT NULL DEFAULT FALSE, version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 UNIQUE(owner_id,review_date), FOREIGN KEY(owner_id) REFERENCES users(id)
);
ALTER TABLE weekly_reviews ADD COLUMN energy VARCHAR(16) NOT NULL DEFAULT 'UNSPECIFIED';
ALTER TABLE weekly_reviews ADD COLUMN burden VARCHAR(16) NOT NULL DEFAULT 'UNSPECIFIED';
ALTER TABLE weekly_reviews ADD COLUMN reviewed BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE weekly_reviews ADD COLUMN open_to_increase BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE restored_weekly_reviews ADD COLUMN energy VARCHAR(16) NOT NULL DEFAULT 'UNSPECIFIED';
ALTER TABLE restored_weekly_reviews ADD COLUMN burden VARCHAR(16) NOT NULL DEFAULT 'UNSPECIFIED';
ALTER TABLE restored_weekly_reviews ADD COLUMN reviewed BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE restored_weekly_reviews ADD COLUMN open_to_increase BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE restored_daily_reviews (
 id VARCHAR(36) PRIMARY KEY, owner_id VARCHAR(36) NOT NULL, batch_id VARCHAR(36) NOT NULL, review_date DATE NOT NULL,
 wins VARCHAR(4000) NOT NULL, blockers VARCHAR(4000) NOT NULL, next_focus VARCHAR(4000) NOT NULL,
 energy VARCHAR(16) NOT NULL, burden VARCHAR(16) NOT NULL, reviewed BOOLEAN NOT NULL,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 FOREIGN KEY(owner_id) REFERENCES users(id), FOREIGN KEY(batch_id) REFERENCES personal_restore_batches(id)
);
CREATE INDEX idx_restored_daily_owner ON restored_daily_reviews(owner_id,review_date);
CREATE TABLE reflection_preferences (
 owner_id VARCHAR(36) PRIMARY KEY, config_json TEXT NOT NULL, version BIGINT NOT NULL,
 FOREIGN KEY(owner_id) REFERENCES users(id)
);
CREATE TABLE reflection_guides (
 owner_id VARCHAR(36) NOT NULL, guide_kind VARCHAR(20) NOT NULL, period_start DATE NOT NULL, claimed_at TIMESTAMP(6) NOT NULL,
 PRIMARY KEY(owner_id,guide_kind,period_start), FOREIGN KEY(owner_id) REFERENCES users(id)
);
CREATE TABLE reflection_coaching (
 owner_id VARCHAR(36) PRIMARY KEY, offered_at TIMESTAMP(6) NOT NULL, week_start DATE NOT NULL, decision VARCHAR(16) NOT NULL,
 FOREIGN KEY(owner_id) REFERENCES users(id)
);
ALTER TABLE ai_sessions ADD COLUMN review_context_json TEXT;
