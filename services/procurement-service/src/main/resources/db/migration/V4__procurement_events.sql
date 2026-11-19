CREATE TABLE procurement_event_versions (
 purchase_order_id BIGINT NOT NULL PRIMARY KEY,
 revision BIGINT NOT NULL,
 FOREIGN KEY(purchase_order_id) REFERENCES purchase_orders(id)
);
CREATE TABLE procurement_outbox (
 event_id VARCHAR(36) NOT NULL PRIMARY KEY,
 event_type VARCHAR(64) NOT NULL,
 aggregate_id BIGINT NOT NULL,
 aggregate_version BIGINT NOT NULL,
 payload LONGTEXT NOT NULL,
 occurred_at DATETIME(6) NOT NULL,
 published_at DATETIME(6),
 attempt_count INTEGER NOT NULL DEFAULT 0,
 UNIQUE(aggregate_id,aggregate_version)
);
CREATE INDEX ix_procurement_outbox_pending ON procurement_outbox(published_at,occurred_at);
CREATE TABLE procurement_event_inbox (
 consumer_name VARCHAR(64) NOT NULL,
 event_id VARCHAR(36) NOT NULL,
 processed_at DATETIME(6) NOT NULL,
 PRIMARY KEY(consumer_name,event_id)
);
CREATE TABLE procurement_receiving_projections (
 purchase_order_item_id BIGINT NOT NULL PRIMARY KEY,
 inspected_quantity INTEGER NOT NULL CHECK(inspected_quantity >= 0),
 accepted_quantity INTEGER NOT NULL CHECK(accepted_quantity >= 0),
 source_version BIGINT NOT NULL CHECK(source_version >= 0),
 FOREIGN KEY(purchase_order_item_id) REFERENCES purchase_order_items(id)
);
