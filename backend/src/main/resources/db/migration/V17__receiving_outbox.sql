CREATE TABLE receiving_event_versions (
 purchase_order_id BIGINT PRIMARY KEY,
 revision BIGINT NOT NULL
);
CREATE TABLE receiving_outbox (
 event_id VARCHAR(36) PRIMARY KEY,
 event_type VARCHAR(64) NOT NULL,
 aggregate_id BIGINT NOT NULL,
 aggregate_version BIGINT NOT NULL,
 payload LONGTEXT NOT NULL,
 occurred_at DATETIME(6) NOT NULL,
 published_at DATETIME(6),
 attempt_count INTEGER NOT NULL DEFAULT 0,
 UNIQUE(aggregate_id,aggregate_version)
);
CREATE INDEX ix_receiving_outbox_pending ON receiving_outbox(published_at,occurred_at);
