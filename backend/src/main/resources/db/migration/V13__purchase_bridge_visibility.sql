ALTER TABLE purchase_orders ADD COLUMN bridge_confirmed BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE purchase_create_operations ADD COLUMN purchase_order_id BIGINT;

-- V12 stored PurchaseOrderCommandResponse with id as its first JSON property.
-- Match the complete numeric token so order 12 cannot match order 123.
UPDATE purchase_create_operations operation
SET purchase_order_id = (
    SELECT purchase_order.id FROM purchase_orders purchase_order
    WHERE operation.response_payload LIKE CONCAT('{"id":', purchase_order.id, ',%')
)
WHERE actor_roles IS NOT NULL AND response_payload IS NOT NULL;

UPDATE purchase_orders purchase_order SET bridge_confirmed = FALSE
WHERE EXISTS (
    SELECT 1 FROM purchase_create_operations operation
    WHERE operation.purchase_order_id = purchase_order.id
      AND operation.actor_roles IS NOT NULL AND operation.state = 'PENDING'
);

ALTER TABLE purchase_create_operations
    ADD CONSTRAINT uq_purchase_operation_order UNIQUE (purchase_order_id);
ALTER TABLE purchase_create_operations
    ADD CONSTRAINT fk_purchase_operation_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id);
ALTER TABLE purchase_create_operations
    ADD CONSTRAINT ck_bridge_response_order CHECK (
        actor_roles IS NULL OR response_payload IS NULL OR purchase_order_id IS NOT NULL
    );
