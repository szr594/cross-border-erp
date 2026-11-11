CREATE TABLE order_import_batches (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    original_file_name VARCHAR(255) NOT NULL,
    file_sha256 CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_rows INTEGER NOT NULL DEFAULT 0,
    order_count INTEGER NOT NULL DEFAULT 0,
    item_count INTEGER NOT NULL DEFAULT 0,
    creatable_order_count INTEGER NOT NULL DEFAULT 0,
    skipped_order_count INTEGER NOT NULL DEFAULT 0,
    error_count INTEGER NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL REFERENCES app_users(id),
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    confirmed_at DATETIME(6),
    completed_at DATETIME(6),
    failure_code VARCHAR(64),
    CONSTRAINT ck_order_import_batches_status CHECK (
        status IN ('VALIDATING', 'VALIDATED', 'INVALID', 'IMPORTING', 'COMPLETED', 'FAILED', 'EXPIRED')
    )
);

CREATE TABLE order_import_rows (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    batch_id VARCHAR(36) NOT NULL REFERENCES order_import_batches(id),
    csv_row_number INTEGER NOT NULL,
    order_key VARCHAR(300) NOT NULL,
    normalized_payload JSON NOT NULL,
    row_sha256 CHAR(64) NOT NULL,
    row_status VARCHAR(20) NOT NULL,
    CONSTRAINT uq_order_import_rows_batch_row UNIQUE (batch_id, csv_row_number),
    CONSTRAINT ck_order_import_rows_status CHECK (row_status IN ('VALID', 'INVALID'))
);

CREATE TABLE order_import_errors (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    batch_id VARCHAR(36) NOT NULL REFERENCES order_import_batches(id),
    csv_row_number INTEGER NOT NULL,
    field_name VARCHAR(64),
    error_code VARCHAR(64) NOT NULL,
    error_message VARCHAR(500) NOT NULL
);

CREATE INDEX ix_order_import_batches_created_by ON order_import_batches(created_by);
CREATE INDEX ix_order_import_batches_status ON order_import_batches(status);
CREATE INDEX ix_order_import_rows_batch_order_key ON order_import_rows(batch_id, order_key);
CREATE INDEX ix_order_import_errors_batch_row ON order_import_errors(batch_id, csv_row_number, id);
