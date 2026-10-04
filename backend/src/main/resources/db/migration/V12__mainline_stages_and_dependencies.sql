ALTER TABLE mainlines ADD COLUMN execution_mode VARCHAR(20) NOT NULL DEFAULT 'SEQUENTIAL';
CREATE TABLE mainline_stages (
    id VARCHAR(36) PRIMARY KEY,
    mainline_id VARCHAR(36) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL DEFAULT '',
    sort_order INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (mainline_id) REFERENCES mainlines(id) ON DELETE CASCADE
);
CREATE INDEX idx_mainline_stages_line ON mainline_stages(mainline_id, sort_order);
ALTER TABLE mainline_items ADD COLUMN stage_id VARCHAR(36);
ALTER TABLE mainline_items ADD COLUMN dependency_mode VARCHAR(20) NOT NULL DEFAULT 'INHERIT';
ALTER TABLE mainline_items ADD COLUMN prerequisite_ids_json VARCHAR(4000) NOT NULL DEFAULT '[]';
ALTER TABLE mainline_items ADD CONSTRAINT fk_mainline_item_stage FOREIGN KEY (stage_id) REFERENCES mainline_stages(id) ON DELETE SET NULL;
