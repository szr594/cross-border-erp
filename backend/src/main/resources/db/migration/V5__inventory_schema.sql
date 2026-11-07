CREATE TABLE inventory_lots (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    lot_no VARCHAR(64) NOT NULL UNIQUE,
    goods_receipt_item_id BIGINT NOT NULL,
    purchase_order_item_id BIGINT NOT NULL,
    quality_status VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    initial_quantity INTEGER NOT NULL,
    available_quantity INTEGER NOT NULL DEFAULT 0,
    reserved_quantity INTEGER NOT NULL DEFAULT 0,
    quarantined_quantity INTEGER NOT NULL DEFAULT 0,
    consumed_quantity INTEGER NOT NULL DEFAULT 0,
    adjusted_out_quantity INTEGER NOT NULL DEFAULT 0,
    received_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_inventory_lots_receipt_item FOREIGN KEY (goods_receipt_item_id) REFERENCES goods_receipt_items(id),
    CONSTRAINT fk_inventory_lots_purchase_item FOREIGN KEY (purchase_order_item_id) REFERENCES purchase_order_items(id),
    CONSTRAINT uq_inventory_lots_receipt_quality UNIQUE (goods_receipt_item_id, quality_status),
    CONSTRAINT ck_inventory_lots_quality CHECK (quality_status IN ('ACCEPTED', 'DAMAGED')),
    CONSTRAINT ck_inventory_lots_status CHECK (
        status IN ('AVAILABLE', 'ALLOCATED', 'QUARANTINED', 'DEPLETED', 'CLOSED')
    ),
    CONSTRAINT ck_inventory_lots_quantities CHECK (
        initial_quantity > 0
        AND available_quantity >= 0
        AND reserved_quantity >= 0
        AND quarantined_quantity >= 0
        AND consumed_quantity >= 0
        AND adjusted_out_quantity >= 0
        AND initial_quantity = available_quantity + reserved_quantity + quarantined_quantity
            + consumed_quantity + adjusted_out_quantity
    ),
    CONSTRAINT ck_inventory_lots_quality_balance CHECK (
        (quality_status = 'ACCEPTED' AND quarantined_quantity = 0)
        OR
        (quality_status = 'DAMAGED' AND available_quantity = 0 AND reserved_quantity = 0 AND consumed_quantity = 0)
    )
);

CREATE TABLE inventory_allocations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    allocation_no VARCHAR(64) NOT NULL UNIQUE,
    inventory_lot_id BIGINT NOT NULL,
    sales_order_item_id BIGINT NOT NULL,
    purchase_order_allocation_id BIGINT,
    quantity INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    allocated_by BIGINT,
    allocated_at DATETIME(6) NOT NULL,
    released_by BIGINT,
    released_at DATETIME(6),
    release_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_inventory_allocations_lot FOREIGN KEY (inventory_lot_id) REFERENCES inventory_lots(id),
    CONSTRAINT fk_inventory_allocations_order_item FOREIGN KEY (sales_order_item_id) REFERENCES sales_order_items(id),
    CONSTRAINT fk_inventory_allocations_purchase_allocation FOREIGN KEY (purchase_order_allocation_id) REFERENCES purchase_order_allocations(id),
    CONSTRAINT fk_inventory_allocations_allocated_by FOREIGN KEY (allocated_by) REFERENCES app_users(id),
    CONSTRAINT fk_inventory_allocations_released_by FOREIGN KEY (released_by) REFERENCES app_users(id),
    CONSTRAINT uq_inventory_allocations_source UNIQUE (inventory_lot_id, purchase_order_allocation_id),
    CONSTRAINT ck_inventory_allocations_quantity CHECK (quantity > 0),
    CONSTRAINT ck_inventory_allocations_status CHECK (status IN ('RESERVED', 'CONSUMED', 'RELEASED')),
    CONSTRAINT ck_inventory_allocations_release CHECK (
        (status = 'RELEASED' AND released_at IS NOT NULL AND release_reason IS NOT NULL)
        OR
        (status <> 'RELEASED' AND released_at IS NULL AND released_by IS NULL AND release_reason IS NULL)
    )
);

CREATE TABLE inventory_movements (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    movement_no VARCHAR(64) NOT NULL UNIQUE,
    inventory_lot_id BIGINT NOT NULL,
    allocation_id BIGINT,
    movement_type VARCHAR(32) NOT NULL,
    quantity INTEGER NOT NULL,
    available_after INTEGER NOT NULL,
    reserved_after INTEGER NOT NULL,
    quarantined_after INTEGER NOT NULL,
    operator_id BIGINT,
    reason VARCHAR(500),
    idempotency_key VARCHAR(64) NOT NULL UNIQUE,
    occurred_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_inventory_movements_lot FOREIGN KEY (inventory_lot_id) REFERENCES inventory_lots(id),
    CONSTRAINT fk_inventory_movements_allocation FOREIGN KEY (allocation_id) REFERENCES inventory_allocations(id),
    CONSTRAINT fk_inventory_movements_operator FOREIGN KEY (operator_id) REFERENCES app_users(id),
    CONSTRAINT ck_inventory_movements_type CHECK (
        movement_type IN (
            'RECEIPT_ACCEPTED', 'RECEIPT_DAMAGED', 'ORDER_ALLOCATED',
            'ALLOCATION_RELEASED', 'PACKING_CONSUMED', 'ADJUSTMENT_IN',
            'ADJUSTMENT_OUT', 'QUARANTINE_RELEASED'
        )
    ),
    CONSTRAINT ck_inventory_movements_quantity CHECK (quantity > 0),
    CONSTRAINT ck_inventory_movements_balances CHECK (
        available_after >= 0 AND reserved_after >= 0 AND quarantined_after >= 0
    )
);

CREATE INDEX ix_inventory_lots_purchase_item ON inventory_lots(purchase_order_item_id);
CREATE INDEX ix_inventory_lots_status ON inventory_lots(status);
CREATE INDEX ix_inventory_lots_received_at ON inventory_lots(received_at);
CREATE INDEX ix_inventory_allocations_lot ON inventory_allocations(inventory_lot_id);
CREATE INDEX ix_inventory_allocations_order_item ON inventory_allocations(sales_order_item_id);
CREATE INDEX ix_inventory_allocations_status ON inventory_allocations(status);
CREATE INDEX ix_inventory_movements_lot_occurred ON inventory_movements(inventory_lot_id, occurred_at);
