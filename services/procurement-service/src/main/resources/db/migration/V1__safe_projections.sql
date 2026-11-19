CREATE TABLE user_display_projections (
    id BIGINT NOT NULL PRIMARY KEY,
    login_name VARCHAR(100) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    source_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_user_projection_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ck_user_projection_version CHECK (source_version >= 0)
);

CREATE TABLE procurement_order_projections (
 order_id BIGINT PRIMARY KEY, order_no VARCHAR(32) NOT NULL, identity_key VARCHAR(300) NOT NULL UNIQUE,
 external_status VARCHAR(64) NOT NULL, fulfillment_exception BOOLEAN NOT NULL,
 source_version BIGINT NOT NULL CHECK(source_version >= 0)
);
CREATE TABLE procurement_item_projections (
 order_item_id BIGINT PRIMARY KEY, order_id BIGINT NOT NULL,
 product_name VARCHAR(500) NOT NULL, external_sku VARCHAR(128), variant_name VARCHAR(500),
 quantity INTEGER NOT NULL CHECK(quantity > 0), fulfillment_status VARCHAR(32) NOT NULL,
 source_version BIGINT NOT NULL CHECK(source_version >= 0),
 FOREIGN KEY(order_id) REFERENCES procurement_order_projections(order_id)
);
CREATE TABLE procurement_request_projections (
 request_id BIGINT PRIMARY KEY, order_item_id BIGINT NOT NULL,
 requested_quantity INTEGER NOT NULL CHECK(requested_quantity > 0),
 status VARCHAR(32) NOT NULL CHECK(status IN ('REQUESTED','CONVERTED','WITHDRAWN')),
 source_version BIGINT NOT NULL CHECK(source_version >= 0),
 FOREIGN KEY(order_item_id) REFERENCES procurement_item_projections(order_item_id)
);
