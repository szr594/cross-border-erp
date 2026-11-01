CREATE TABLE sales_channels (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE sales_stores (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    channel_id BIGINT NOT NULL REFERENCES sales_channels(id),
    external_store_id VARCHAR(128) NOT NULL,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    current_owner_id BIGINT REFERENCES app_users(id),
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_sales_stores_channel_external UNIQUE (channel_id, external_store_id),
    CONSTRAINT ck_sales_stores_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE sales_orders (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL UNIQUE,
    store_id BIGINT NOT NULL REFERENCES sales_stores(id),
    external_order_id VARCHAR(128) NOT NULL,
    display_order_no VARCHAR(128),
    external_status VARCHAR(64) NOT NULL,
    external_status_raw VARCHAR(128),
    fulfillment_exception BOOLEAN NOT NULL DEFAULT FALSE,
    exception_reason VARCHAR(500),
    responsible_user_id BIGINT REFERENCES app_users(id),
    currency CHAR(3),
    ordered_at DATETIME(6),
    ship_after DATETIME(6),
    requested_ship_at DATETIME(6),
    external_updated_at DATETIME(6),
    customer_id VARCHAR(128),
    customer_name VARCHAR(200),
    recipient_name VARCHAR(200),
    recipient_phone VARCHAR(64),
    country_code CHAR(2),
    province VARCHAR(128),
    city VARCHAR(128),
    district VARCHAR(128),
    address_line1 VARCHAR(500),
    address_line2 VARCHAR(500),
    postal_code VARCHAR(32),
    note VARCHAR(1000),
    source_payload JSON,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_sales_orders_store_external UNIQUE (store_id, external_order_id),
    CONSTRAINT ck_sales_orders_external_status CHECK (
        external_status IN ('NEW', 'PROCESSING', 'PRE_TRANSIT', 'IN_TRANSIT', 'DELIVERED', 'CANCELED', 'UNKNOWN')
    )
);

CREATE TABLE sales_order_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES sales_orders(id),
    external_item_id VARCHAR(128) NOT NULL,
    external_product_id VARCHAR(128),
    external_variant_id VARCHAR(128),
    external_sku VARCHAR(128),
    erp_sku_id BIGINT,
    product_name VARCHAR(500) NOT NULL,
    variant_name VARCHAR(500),
    image_url VARCHAR(1000),
    quantity INTEGER NOT NULL,
    unit_price_minor BIGINT,
    currency CHAR(3),
    fulfillment_status VARCHAR(32) NOT NULL,
    note VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_sales_order_items_order_external UNIQUE (order_id, external_item_id),
    CONSTRAINT ck_sales_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_sales_order_items_fulfillment_status CHECK (
        fulfillment_status IN ('NORMAL', 'CANCELED', 'RETURNED')
    )
);

CREATE TABLE order_procurement_requests (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_item_id BIGINT NOT NULL REFERENCES sales_order_items(id),
    requested_quantity INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    requested_by BIGINT NOT NULL REFERENCES app_users(id),
    requested_at DATETIME(6) NOT NULL,
    withdrawn_by BIGINT REFERENCES app_users(id),
    withdrawn_at DATETIME(6),
    note VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_order_procurement_requests_quantity CHECK (requested_quantity > 0),
    CONSTRAINT ck_order_procurement_requests_status CHECK (
        status IN ('REQUESTED', 'CONVERTED', 'WITHDRAWN')
    )
);

CREATE TABLE order_status_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES sales_orders(id),
    event_type VARCHAR(64) NOT NULL,
    from_value VARCHAR(128),
    to_value VARCHAR(128),
    operator_id BIGINT REFERENCES app_users(id),
    source VARCHAR(32) NOT NULL,
    remark VARCHAR(500),
    occurred_at DATETIME(6) NOT NULL,
    CONSTRAINT ck_order_status_events_source CHECK (source IN ('USER', 'IMPORT', 'SYSTEM'))
);

CREATE INDEX ix_sales_stores_channel_id ON sales_stores(channel_id);
CREATE INDEX ix_sales_stores_current_owner_id ON sales_stores(current_owner_id);
CREATE INDEX ix_sales_orders_store_id ON sales_orders(store_id);
CREATE INDEX ix_sales_orders_responsible_user_id ON sales_orders(responsible_user_id);
CREATE INDEX ix_sales_orders_external_status ON sales_orders(external_status);
CREATE INDEX ix_sales_orders_ordered_at ON sales_orders(ordered_at);
CREATE INDEX ix_sales_orders_requested_ship_at ON sales_orders(requested_ship_at);
CREATE INDEX ix_sales_orders_customer_id ON sales_orders(customer_id);
CREATE INDEX ix_sales_order_items_order_id ON sales_order_items(order_id);
CREATE INDEX ix_sales_order_items_erp_sku_id ON sales_order_items(erp_sku_id);
CREATE INDEX ix_order_procurement_requests_item_id ON order_procurement_requests(order_item_id);
CREATE INDEX ix_order_procurement_requests_requested_by ON order_procurement_requests(requested_by);
CREATE INDEX ix_order_status_events_order_id ON order_status_events(order_id);
CREATE INDEX ix_order_status_events_occurred_at ON order_status_events(occurred_at);
