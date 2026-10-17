CREATE TABLE feedback_tickets (
    id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    operation_key VARCHAR(36) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    category VARCHAR(24) NOT NULL,
    title VARCHAR(120) NOT NULL,
    steps VARCHAR(4000) NOT NULL,
    expected_result VARCHAR(2000) NOT NULL,
    actual_result VARCHAR(2000) NOT NULL,
    diagnostic_json VARCHAR(2000),
    status VARCHAR(24) NOT NULL,
    activity BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE (owner_id, operation_key),
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_feedback_owner ON feedback_tickets(owner_id, updated_at, id);
CREATE INDEX idx_feedback_status ON feedback_tickets(status, updated_at, id);
CREATE TABLE feedback_messages (
    id VARCHAR(36) PRIMARY KEY,
    ticket_id VARCHAR(36) NOT NULL,
    author_kind VARCHAR(12) NOT NULL,
    body VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (ticket_id) REFERENCES feedback_tickets(id) ON DELETE CASCADE
);
CREATE INDEX idx_feedback_messages ON feedback_messages(ticket_id, created_at, id);
