ALTER TABLE order_business_projections ADD COLUMN authority VARCHAR(16) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE order_business_projections ADD COLUMN identity_key VARCHAR(512);
ALTER TABLE order_business_projections ADD COLUMN store_name VARCHAR(200);
ALTER TABLE order_business_projections ADD COLUMN requested_ship_at DATETIME(6);
ALTER TABLE order_item_business_projections ADD COLUMN variant_name VARCHAR(500);
ALTER TABLE order_item_business_projections ADD COLUMN external_item_id VARCHAR(128);
ALTER TABLE order_item_business_projections ADD COLUMN erp_sku_id BIGINT;

-- Include legacy rows created after the original V10 backfill.
INSERT IGNORE INTO order_business_projections(order_id,order_no,external_status,fulfillment_exception,responsible_user_id,source_version,updated_at)
SELECT id,order_no,external_status,fulfillment_exception,responsible_user_id,version,updated_at FROM sales_orders;
INSERT IGNORE INTO order_item_business_projections(order_item_id,order_id,product_name,external_sku,quantity,fulfillment_status,source_version)
SELECT i.id,i.order_id,i.product_name,i.external_sku,i.quantity,i.fulfillment_status,o.version FROM sales_order_items i JOIN sales_orders o ON o.id=i.order_id;
UPDATE order_business_projections p SET authority='ORDER'
WHERE NOT EXISTS (SELECT 1 FROM sales_orders o WHERE o.id=p.order_id);

UPDATE order_business_projections p SET
    identity_key=(SELECT CONCAT(o.store_id, ':', o.external_order_id) FROM sales_orders o WHERE o.id=p.order_id),
    store_name=(SELECT s.name FROM sales_orders o JOIN sales_stores s ON s.id=o.store_id WHERE o.id=p.order_id),
    requested_ship_at=(SELECT o.requested_ship_at FROM sales_orders o WHERE o.id=p.order_id);
UPDATE order_item_business_projections p SET
    variant_name=(SELECT i.variant_name FROM sales_order_items i WHERE i.id=p.order_item_id),
    external_item_id=(SELECT i.external_item_id FROM sales_order_items i WHERE i.id=p.order_item_id),
    erp_sku_id=(SELECT i.erp_sku_id FROM sales_order_items i WHERE i.id=p.order_item_id);

CREATE TABLE procurement_request_projections (
    request_id BIGINT NOT NULL PRIMARY KEY,
    order_item_id BIGINT NOT NULL,
    requested_quantity INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    source_version BIGINT NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_demand_projection_item FOREIGN KEY(order_item_id) REFERENCES order_item_business_projections(order_item_id),
    CONSTRAINT ck_demand_projection_quantity CHECK(requested_quantity>0),
    CONSTRAINT ck_demand_projection_status CHECK(status IN ('REQUESTED','CONVERTED','WITHDRAWN'))
);
INSERT INTO procurement_request_projections(request_id,order_item_id,requested_quantity,status,source_version,requested_at)
SELECT id,order_item_id,requested_quantity,status,version,requested_at FROM order_procurement_requests;
