CREATE TABLE packing_tasks (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_no VARCHAR(64) NOT NULL UNIQUE,
    sales_order_id BIGINT NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    started_by BIGINT NOT NULL,
    started_at DATETIME(6) NOT NULL,
    completed_by BIGINT,
    completed_at DATETIME(6),
    canceled_by BIGINT,
    canceled_at DATETIME(6),
    cancel_reason VARCHAR(500),
    completion_key VARCHAR(64) UNIQUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_packing_tasks_order FOREIGN KEY (sales_order_id) REFERENCES sales_orders(id),
    CONSTRAINT fk_packing_tasks_started_by FOREIGN KEY (started_by) REFERENCES app_users(id),
    CONSTRAINT fk_packing_tasks_completed_by FOREIGN KEY (completed_by) REFERENCES app_users(id),
    CONSTRAINT fk_packing_tasks_canceled_by FOREIGN KEY (canceled_by) REFERENCES app_users(id),
    CONSTRAINT ck_packing_tasks_status CHECK (status IN ('DRAFT', 'COMPLETED', 'CANCELED')),
    CONSTRAINT ck_packing_tasks_completed CHECK (
        (status = 'COMPLETED' AND completed_by IS NOT NULL AND completed_at IS NOT NULL AND completion_key IS NOT NULL)
        OR status <> 'COMPLETED'
    ),
    CONSTRAINT ck_packing_tasks_canceled CHECK (
        (status = 'CANCELED' AND canceled_by IS NOT NULL AND canceled_at IS NOT NULL AND cancel_reason IS NOT NULL)
        OR status <> 'CANCELED'
    )
);

CREATE TABLE packing_boxes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    box_no VARCHAR(64) NOT NULL UNIQUE,
    packing_task_id BIGINT NOT NULL,
    weight_grams INTEGER,
    length_mm INTEGER,
    width_mm INTEGER,
    height_mm INTEGER,
    note VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_packing_boxes_task FOREIGN KEY (packing_task_id) REFERENCES packing_tasks(id),
    CONSTRAINT ck_packing_boxes_weight CHECK (weight_grams IS NULL OR weight_grams > 0),
    CONSTRAINT ck_packing_boxes_dimensions CHECK (
        (length_mm IS NULL AND width_mm IS NULL AND height_mm IS NULL)
        OR (length_mm > 0 AND width_mm > 0 AND height_mm > 0)
    )
);

CREATE TABLE packing_box_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    packing_box_id BIGINT NOT NULL,
    sales_order_item_id BIGINT NOT NULL,
    inventory_allocation_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    CONSTRAINT fk_packing_box_items_box FOREIGN KEY (packing_box_id) REFERENCES packing_boxes(id),
    CONSTRAINT fk_packing_box_items_order_item FOREIGN KEY (sales_order_item_id) REFERENCES sales_order_items(id),
    CONSTRAINT fk_packing_box_items_allocation FOREIGN KEY (inventory_allocation_id) REFERENCES inventory_allocations(id),
    CONSTRAINT uq_packing_box_items_box_allocation UNIQUE (packing_box_id, inventory_allocation_id),
    CONSTRAINT ck_packing_box_items_quantity CHECK (quantity > 0)
);

CREATE TABLE packing_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    packing_task_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    operator_id BIGINT,
    remark VARCHAR(500),
    occurred_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_packing_events_task FOREIGN KEY (packing_task_id) REFERENCES packing_tasks(id),
    CONSTRAINT fk_packing_events_operator FOREIGN KEY (operator_id) REFERENCES app_users(id),
    CONSTRAINT ck_packing_events_type CHECK (event_type IN ('STARTED', 'BOX_ADDED', 'BOX_UPDATED', 'BOX_REMOVED', 'COMPLETED', 'CANCELED'))
);

CREATE INDEX ix_packing_tasks_status_started ON packing_tasks(status, started_at);
CREATE INDEX ix_packing_boxes_task ON packing_boxes(packing_task_id);
CREATE INDEX ix_packing_box_items_order_item ON packing_box_items(sales_order_item_id);
CREATE INDEX ix_packing_box_items_allocation ON packing_box_items(inventory_allocation_id);
CREATE INDEX ix_packing_events_task_occurred ON packing_events(packing_task_id, occurred_at);
