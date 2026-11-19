CREATE TABLE purchase_create_operations (
    idempotency_key VARCHAR(64) NOT NULL PRIMARY KEY,
    actor_id BIGINT NOT NULL,
    request_hash CHAR(64) NOT NULL,
    request_payload LONGTEXT NOT NULL,
    state VARCHAR(20) NOT NULL,
    response_payload LONGTEXT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT ck_purchase_operation_state CHECK(state IN ('PENDING','SUCCEEDED'))
);
ALTER TABLE purchase_create_operations ADD COLUMN operation_phase VARCHAR(32) NOT NULL DEFAULT 'REGISTERED';
ALTER TABLE purchase_create_operations ADD COLUMN actor_roles VARCHAR(255);
ALTER TABLE purchase_create_operations ADD COLUMN lease_owner VARCHAR(36);
ALTER TABLE purchase_create_operations ADD COLUMN lease_until DATETIME(6);
ALTER TABLE purchase_create_operations ADD COLUMN next_attempt_at DATETIME(6);
ALTER TABLE purchase_create_operations ADD COLUMN error_code VARCHAR(100);
ALTER TABLE purchase_create_operations ADD COLUMN error_status INTEGER;
CREATE INDEX ix_purchase_recovery ON purchase_create_operations(state,next_attempt_at,lease_until);

ALTER TABLE purchase_create_operations ADD COLUMN purchase_order_id BIGINT;
ALTER TABLE purchase_create_operations ADD CONSTRAINT uq_purchase_operation_order UNIQUE(purchase_order_id);
ALTER TABLE purchase_create_operations ADD CONSTRAINT fk_purchase_operation_order FOREIGN KEY(purchase_order_id) REFERENCES purchase_orders(id);
ALTER TABLE purchase_create_operations ADD CONSTRAINT ck_bridge_response_order CHECK(actor_roles IS NULL OR response_payload IS NULL OR purchase_order_id IS NOT NULL);
