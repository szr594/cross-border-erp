ALTER TABLE purchase_create_operations ADD COLUMN operation_phase VARCHAR(32) NOT NULL DEFAULT 'REGISTERED';
ALTER TABLE purchase_create_operations ADD COLUMN actor_roles VARCHAR(255);
ALTER TABLE purchase_create_operations ADD COLUMN lease_owner VARCHAR(36);
ALTER TABLE purchase_create_operations ADD COLUMN lease_until DATETIME(6);
ALTER TABLE purchase_create_operations ADD COLUMN next_attempt_at DATETIME(6);
ALTER TABLE purchase_create_operations ADD COLUMN error_code VARCHAR(100);
ALTER TABLE purchase_create_operations ADD COLUMN error_status INTEGER;
CREATE INDEX ix_purchase_recovery ON purchase_create_operations(state,next_attempt_at,lease_until);
