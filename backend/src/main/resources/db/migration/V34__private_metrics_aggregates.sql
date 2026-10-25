CREATE TABLE web_metric_days (
 metric_day DATE PRIMARY KEY,
 salt VARCHAR(64) NOT NULL,
 pv BIGINT NOT NULL DEFAULT 0,
 uv BIGINT NOT NULL DEFAULT 0,
 uv_unavailable BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE web_metric_visitors (
 metric_day DATE NOT NULL,
 visitor_hash VARCHAR(64) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 PRIMARY KEY(metric_day,visitor_hash)
);
CREATE INDEX idx_web_metric_visitors_expiry ON web_metric_visitors(created_at);
CREATE TABLE web_metric_events (
 ticket_hash VARCHAR(64) PRIMARY KEY,
 created_at TIMESTAMP(6) NOT NULL
);
CREATE INDEX idx_web_metric_events_expiry ON web_metric_events(created_at);
ALTER TABLE operations_mail_delivery ADD COLUMN metric_summary VARCHAR(500);
