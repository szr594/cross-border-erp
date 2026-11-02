CREATE TABLE suppliers (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    platform VARCHAR(32) NOT NULL,
    external_supplier_id VARCHAR(128),
    contact_name VARCHAR(100),
    contact_phone VARCHAR(64),
    shop_url VARCHAR(1000),
    note VARCHAR(500),
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_suppliers_platform_external UNIQUE (platform, external_supplier_id),
    CONSTRAINT ck_suppliers_platform CHECK (platform IN ('MANUAL', 'ALIBABA_1688')),
    CONSTRAINT ck_suppliers_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE purchase_orders (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    purchase_order_no VARCHAR(64) NOT NULL UNIQUE,
    supplier_id BIGINT NOT NULL REFERENCES suppliers(id),
    status VARCHAR(32) NOT NULL,
    external_order_no VARCHAR(128),
    currency VARCHAR(3) NOT NULL,
    total_amount_minor BIGINT NOT NULL DEFAULT 0,
    ordered_at DATETIME(6),
    expected_arrival_at DATETIME(6),
    created_by BIGINT NOT NULL REFERENCES app_users(id),
    ordered_by BIGINT REFERENCES app_users(id),
    canceled_by BIGINT REFERENCES app_users(id),
    canceled_at DATETIME(6),
    cancel_reason VARCHAR(500),
    note VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_purchase_orders_status CHECK (
        status IN ('DRAFT', 'ORDERED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'COMPLETED', 'CANCELED')
    ),
    CONSTRAINT ck_purchase_orders_total_amount CHECK (total_amount_minor >= 0)
);

CREATE TABLE purchase_order_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    purchase_order_id BIGINT NOT NULL REFERENCES purchase_orders(id),
    supplier_product_id VARCHAR(128),
    supplier_sku VARCHAR(128),
    product_name VARCHAR(500) NOT NULL,
    variant_name VARCHAR(500),
    product_url VARCHAR(1000),
    image_url VARCHAR(1000),
    purchased_quantity INTEGER NOT NULL,
    received_quantity INTEGER NOT NULL DEFAULT 0,
    unit_price_minor BIGINT,
    currency VARCHAR(3) NOT NULL,
    note VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_purchase_order_items_purchased_quantity CHECK (purchased_quantity > 0),
    CONSTRAINT ck_purchase_order_items_received_quantity CHECK (
        received_quantity >= 0 AND received_quantity <= purchased_quantity
    ),
    CONSTRAINT ck_purchase_order_items_unit_price CHECK (unit_price_minor IS NULL OR unit_price_minor >= 0)
);

CREATE TABLE purchase_order_allocations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    purchase_order_item_id BIGINT NOT NULL REFERENCES purchase_order_items(id),
    procurement_request_id BIGINT NOT NULL REFERENCES order_procurement_requests(id),
    allocated_quantity INTEGER NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_purchase_order_allocations_item_request UNIQUE (
        purchase_order_item_id, procurement_request_id
    ),
    CONSTRAINT ck_purchase_order_allocations_quantity CHECK (allocated_quantity > 0)
);

CREATE TABLE purchase_order_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    purchase_order_id BIGINT NOT NULL REFERENCES purchase_orders(id),
    event_type VARCHAR(64) NOT NULL,
    from_value VARCHAR(128),
    to_value VARCHAR(128),
    operator_id BIGINT REFERENCES app_users(id),
    remark VARCHAR(500),
    occurred_at DATETIME(6) NOT NULL
);

CREATE INDEX ix_purchase_orders_supplier_id ON purchase_orders(supplier_id);
CREATE INDEX ix_purchase_orders_status ON purchase_orders(status);
CREATE INDEX ix_purchase_order_items_order_id ON purchase_order_items(purchase_order_id);
CREATE INDEX ix_purchase_order_allocations_request_id ON purchase_order_allocations(procurement_request_id);
CREATE INDEX ix_purchase_order_events_order_id ON purchase_order_events(purchase_order_id);
CREATE INDEX ix_purchase_order_events_occurred_at ON purchase_order_events(occurred_at);
