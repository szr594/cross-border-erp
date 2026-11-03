CREATE TABLE domestic_packages (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    package_no VARCHAR(64) NOT NULL UNIQUE,
    purchase_order_id BIGINT NOT NULL,
    tracking_number VARCHAR(128) NOT NULL,
    carrier VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL,
    expected_at DATETIME(6),
    first_received_at DATETIME(6),
    completed_at DATETIME(6),
    created_by BIGINT NOT NULL,
    note VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_domestic_packages_purchase_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
    CONSTRAINT fk_domestic_packages_created_by FOREIGN KEY (created_by) REFERENCES app_users(id),
    CONSTRAINT uq_domestic_packages_tracking UNIQUE (carrier, tracking_number),
    CONSTRAINT ck_domestic_packages_status CHECK (
        status IN ('EXPECTED', 'RECEIVING', 'RECEIVED', 'EXCEPTION', 'CANCELED')
    )
);

CREATE TABLE goods_receipts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    receipt_no VARCHAR(64) NOT NULL UNIQUE,
    domestic_package_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    operator_id BIGINT NOT NULL,
    confirmed_at DATETIME(6) NOT NULL,
    note VARCHAR(500),
    idempotency_key VARCHAR(64) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_goods_receipts_package FOREIGN KEY (domestic_package_id) REFERENCES domestic_packages(id),
    CONSTRAINT fk_goods_receipts_operator FOREIGN KEY (operator_id) REFERENCES app_users(id),
    CONSTRAINT ck_goods_receipts_status CHECK (status = 'CONFIRMED')
);

CREATE TABLE goods_receipt_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    goods_receipt_id BIGINT NOT NULL,
    purchase_order_item_id BIGINT NOT NULL,
    accepted_quantity INTEGER NOT NULL DEFAULT 0,
    damaged_quantity INTEGER NOT NULL DEFAULT 0,
    note VARCHAR(500),
    CONSTRAINT fk_goods_receipt_items_receipt FOREIGN KEY (goods_receipt_id) REFERENCES goods_receipts(id),
    CONSTRAINT fk_goods_receipt_items_purchase_item FOREIGN KEY (purchase_order_item_id) REFERENCES purchase_order_items(id),
    CONSTRAINT uq_goods_receipt_items_receipt_item UNIQUE (goods_receipt_id, purchase_order_item_id),
    CONSTRAINT ck_goods_receipt_items_quantities CHECK (
        accepted_quantity >= 0 AND damaged_quantity >= 0 AND accepted_quantity + damaged_quantity > 0
    )
);

CREATE TABLE unexpected_receipt_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    goods_receipt_id BIGINT NOT NULL,
    description VARCHAR(500) NOT NULL,
    supplier_sku VARCHAR(128),
    quantity INTEGER NOT NULL,
    note VARCHAR(500),
    CONSTRAINT fk_unexpected_receipt_items_receipt FOREIGN KEY (goods_receipt_id) REFERENCES goods_receipts(id),
    CONSTRAINT ck_unexpected_receipt_items_quantity CHECK (quantity > 0)
);

CREATE TABLE receiving_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    domestic_package_id BIGINT NOT NULL,
    goods_receipt_id BIGINT,
    event_type VARCHAR(64) NOT NULL,
    operator_id BIGINT NOT NULL,
    remark VARCHAR(500),
    occurred_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_receiving_events_package FOREIGN KEY (domestic_package_id) REFERENCES domestic_packages(id),
    CONSTRAINT fk_receiving_events_receipt FOREIGN KEY (goods_receipt_id) REFERENCES goods_receipts(id),
    CONSTRAINT fk_receiving_events_operator FOREIGN KEY (operator_id) REFERENCES app_users(id)
);

CREATE INDEX ix_domestic_packages_purchase_order ON domestic_packages(purchase_order_id);
CREATE INDEX ix_domestic_packages_status ON domestic_packages(status);
CREATE INDEX ix_domestic_packages_tracking_number ON domestic_packages(tracking_number);
CREATE INDEX ix_goods_receipts_package ON goods_receipts(domestic_package_id);
CREATE INDEX ix_goods_receipts_confirmed_at ON goods_receipts(confirmed_at);
CREATE INDEX ix_goods_receipt_items_purchase_item ON goods_receipt_items(purchase_order_item_id);
CREATE INDEX ix_receiving_events_package ON receiving_events(domestic_package_id);
CREATE INDEX ix_receiving_events_occurred_at ON receiving_events(occurred_at);
