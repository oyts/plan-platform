ALTER TABLE mainlines ADD COLUMN paused BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE orchestration_operations (
    id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    operation_key VARCHAR(36) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    result_json TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_orchestration_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT uq_orchestration_owner_key UNIQUE (owner_id, operation_key)
);
CREATE INDEX idx_todos_owner_date_done ON todos(owner_id, deleted, due_date, done);
CREATE INDEX idx_route_items_status ON mainline_items(mainline_id, status, sort_order);
