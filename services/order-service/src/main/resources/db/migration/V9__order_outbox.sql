CREATE TABLE outbox_events (
    event_id VARCHAR(36) NOT NULL PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    aggregate_version BIGINT NOT NULL,
    payload LONGTEXT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    published_at DATETIME(6),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6),
    CONSTRAINT uq_order_outbox_version UNIQUE(event_type,aggregate_id,aggregate_version)
);
CREATE INDEX ix_order_outbox_pending ON outbox_events(published_at,next_attempt_at);
