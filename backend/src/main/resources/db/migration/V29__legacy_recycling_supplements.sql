CREATE TABLE legacy_recycling_supplements (
    id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    import_id VARCHAR(36) NOT NULL,
    operation_key VARCHAR(36) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    result_json TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_legacy_supplement_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT fk_legacy_supplement_import FOREIGN KEY (import_id) REFERENCES legacy_imports(id),
    CONSTRAINT uq_legacy_supplement_import UNIQUE (import_id),
    CONSTRAINT uq_legacy_supplement_key UNIQUE (owner_id, operation_key)
);
