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
