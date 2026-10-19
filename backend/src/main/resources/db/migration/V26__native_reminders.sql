ALTER TABLE task_progress ADD COLUMN ever_started BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE task_progress SET ever_started=TRUE WHERE started_at IS NOT NULL OR accumulated_seconds>0 OR status IN ('RUNNING','PAUSED','PARTIAL','COMPLETED') OR id IN (SELECT progress_id FROM time_entries);

CREATE TABLE native_reminder_preferences (
  owner_id VARCHAR(36) PRIMARY KEY,
  config_json TEXT NOT NULL,
  version BIGINT NOT NULL,
  FOREIGN KEY (owner_id) REFERENCES users(id)
);
CREATE TABLE native_reminder_devices (
  id VARCHAR(36) PRIMARY KEY,
  owner_id VARCHAR(36) NOT NULL,
  platform VARCHAR(12) NOT NULL,
  label VARCHAR(40) NOT NULL,
  token_hash VARCHAR(64) NOT NULL,
  account_stamp VARCHAR(64) NOT NULL,
  enabled BOOLEAN NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  last_seen_at TIMESTAMP(6),
  created_at TIMESTAMP(6) NOT NULL,
  FOREIGN KEY (owner_id) REFERENCES users(id),
  UNIQUE (token_hash)
);
CREATE INDEX idx_reminder_device_owner ON native_reminder_devices(owner_id);
CREATE TABLE native_reminder_deliveries (
  device_id VARCHAR(36) NOT NULL,
  event_key VARCHAR(64) NOT NULL,
  claimed_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (device_id,event_key),
  FOREIGN KEY (device_id) REFERENCES native_reminder_devices(id)
);
