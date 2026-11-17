CREATE TABLE fulfillment_execution_holds (
    operation_id VARCHAR(36) NOT NULL,
    order_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY(operation_id,order_id),
    CONSTRAINT fk_execution_hold_order FOREIGN KEY(order_id) REFERENCES sales_orders(id),
    CONSTRAINT ck_execution_hold_status CHECK(status IN ('ACTIVE','RELEASED'))
);
CREATE INDEX ix_execution_hold_order ON fulfillment_execution_holds(order_id,status);
CREATE TABLE order_cancel_operations (
    idempotency_key VARCHAR(64) NOT NULL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    expected_version BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    state VARCHAR(16) NOT NULL,
    failure_code VARCHAR(100),
    completed_version BIGINT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_order_cancel_order FOREIGN KEY(order_id) REFERENCES sales_orders(id),
    CONSTRAINT ck_order_cancel_state CHECK(state IN ('PENDING','SUCCEEDED','FAILED'))
);
CREATE INDEX ix_order_cancel_pending ON order_cancel_operations(order_id,state);
