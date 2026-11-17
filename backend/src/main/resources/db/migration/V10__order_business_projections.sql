CREATE TABLE order_business_projections (
    order_id BIGINT NOT NULL PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL,
    external_status VARCHAR(64) NOT NULL,
    fulfillment_exception BOOLEAN NOT NULL,
    responsible_user_id BIGINT,
    source_version BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL
);
CREATE TABLE order_item_business_projections (
    order_item_id BIGINT NOT NULL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_name VARCHAR(500) NOT NULL,
    external_sku VARCHAR(128),
    quantity INTEGER NOT NULL,
    fulfillment_status VARCHAR(32) NOT NULL,
    source_version BIGINT NOT NULL,
    CONSTRAINT fk_business_item_order FOREIGN KEY(order_id) REFERENCES order_business_projections(order_id),
    CONSTRAINT ck_business_item_quantity CHECK(quantity>0)
);
INSERT INTO order_business_projections(order_id,order_no,external_status,fulfillment_exception,responsible_user_id,source_version,updated_at)
SELECT id,order_no,external_status,fulfillment_exception,responsible_user_id,version,updated_at FROM sales_orders;
INSERT INTO order_item_business_projections(order_item_id,order_id,product_name,external_sku,quantity,fulfillment_status,source_version)
SELECT i.id,i.order_id,i.product_name,i.external_sku,i.quantity,i.fulfillment_status,o.version
FROM sales_order_items i JOIN sales_orders o ON o.id=i.order_id;
