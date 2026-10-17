CREATE TABLE reminder_preferences (
    id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    include_plans BOOLEAN NOT NULL DEFAULT TRUE,
    include_todos BOOLEAN NOT NULL DEFAULT TRUE,
    show_overdue BOOLEAN NOT NULL DEFAULT TRUE,
    look_ahead_days INT NOT NULL DEFAULT 3,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE TABLE weekly_reviews (
    id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    week_start DATE NOT NULL,
    wins VARCHAR(4000) NOT NULL DEFAULT '',
    blockers VARCHAR(4000) NOT NULL DEFAULT '',
    next_focus VARCHAR(4000) NOT NULL DEFAULT '',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE (owner_id, week_start),
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
);
-- Old completed todos deliberately remain NULL: updated_at is not a completion date.
ALTER TABLE todos ADD COLUMN completed_at TIMESTAMP(6);
CREATE INDEX idx_todos_completed ON todos(owner_id, deleted, completed_at);
