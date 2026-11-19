CREATE TABLE purchase_supplier_projections (
 id BIGINT PRIMARY KEY, code VARCHAR(64) NOT NULL, name VARCHAR(200) NOT NULL
);
CREATE TABLE purchase_business_projections (
 id BIGINT PRIMARY KEY, purchase_order_no VARCHAR(64) NOT NULL UNIQUE,
 supplier_id BIGINT NOT NULL, status VARCHAR(32) NOT NULL, currency VARCHAR(3) NOT NULL,
 bridge_confirmed BOOLEAN NOT NULL DEFAULT TRUE,
 source_version BIGINT NOT NULL DEFAULT -1, snapshot_hash VARCHAR(64),
 authority VARCHAR(16) NOT NULL DEFAULT 'LEGACY', version BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY (supplier_id) REFERENCES purchase_supplier_projections(id),
 CHECK (authority IN ('LEGACY','PROCUREMENT'))
);
CREATE TABLE purchase_item_projections (
    id BIGINT NOT NULL PRIMARY KEY,
    purchase_order_id BIGINT NOT NULL ,
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
    FOREIGN KEY (purchase_order_id) REFERENCES purchase_business_projections(id),
    CONSTRAINT ck_purchase_projection_items_purchased_quantity CHECK (purchased_quantity > 0),
    CONSTRAINT ck_purchase_projection_items_received_quantity CHECK (
        received_quantity >= 0 AND received_quantity <= purchased_quantity
    ),
    CONSTRAINT ck_purchase_projection_items_unit_price CHECK (unit_price_minor IS NULL OR unit_price_minor >= 0)
);


CREATE TABLE purchase_allocation_projections (
 id BIGINT PRIMARY KEY, purchase_order_item_id BIGINT NOT NULL,
 procurement_request_id BIGINT NOT NULL, allocated_quantity INT NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY (purchase_order_item_id) REFERENCES purchase_item_projections(id),
 FOREIGN KEY (procurement_request_id) REFERENCES procurement_request_projections(request_id),
 UNIQUE (purchase_order_item_id,procurement_request_id), CHECK (allocated_quantity>0)
);
CREATE TABLE procurement_event_inbox (
 consumer_name VARCHAR(100) NOT NULL, event_id VARCHAR(36) NOT NULL,
 processed_at DATETIME(6) NOT NULL, PRIMARY KEY(consumer_name,event_id)
);
INSERT INTO purchase_supplier_projections(id,code,name) SELECT id,code,name FROM suppliers;
INSERT INTO purchase_business_projections(id,purchase_order_no,supplier_id,status,currency,bridge_confirmed,source_version,authority,version)
 SELECT id,purchase_order_no,supplier_id,status,currency,bridge_confirmed,version,'LEGACY',version FROM purchase_orders;
INSERT INTO purchase_item_projections SELECT * FROM purchase_order_items;
INSERT INTO purchase_allocation_projections SELECT * FROM purchase_order_allocations;
