CREATE TABLE fulfillment_execution_operations (
    operation_id VARCHAR(36) NOT NULL PRIMARY KEY,
    order_ids LONGTEXT NOT NULL,
    state VARCHAR(16) NOT NULL,
    lease_until DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_execution_operation_state CHECK(state IN ('ACQUIRING','READY','COMMITTED','RELEASING','RELEASED'))
);
CREATE INDEX ix_execution_recovery ON fulfillment_execution_operations(state,lease_until);
CREATE TABLE order_cancellation_receipts (
    idempotency_key VARCHAR(64) NOT NULL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_cancel_receipt_order FOREIGN KEY(order_id) REFERENCES order_business_projections(order_id)
);
