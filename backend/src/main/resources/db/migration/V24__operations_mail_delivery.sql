CREATE TABLE operations_mail_delivery (
    id VARCHAR(36) PRIMARY KEY,
    incident_id VARCHAR(36) NOT NULL,
    code VARCHAR(40) NOT NULL,
    event_kind VARCHAR(20) NOT NULL,
    state VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE (incident_id,event_kind)
);
CREATE INDEX idx_operations_mail_pending ON operations_mail_delivery(state,created_at,id);
