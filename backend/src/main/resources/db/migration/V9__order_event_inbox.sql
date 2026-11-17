CREATE TABLE order_event_inbox (
    consumer_name VARCHAR(64) NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    PRIMARY KEY(consumer_name,event_id)
);
CREATE TABLE order_event_projections (
    order_id BIGINT NOT NULL PRIMARY KEY,
    source_version BIGINT NOT NULL,
    items_payload LONGTEXT NOT NULL,
    updated_at DATETIME(6) NOT NULL
);
