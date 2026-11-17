CREATE TABLE procurement_claims (
    claim_id VARCHAR(64) NOT NULL PRIMARY KEY,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    purchase_reference VARCHAR(64),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT ck_claim_status CHECK(status IN ('CLAIMED','CONFIRMED','RELEASED'))
);
CREATE TABLE procurement_claim_owners (
    request_id BIGINT NOT NULL PRIMARY KEY,
    claim_id VARCHAR(64) NOT NULL,
    CONSTRAINT fk_claim_owner_claim FOREIGN KEY(claim_id) REFERENCES procurement_claims(claim_id),
    CONSTRAINT fk_claim_owner_request FOREIGN KEY(request_id) REFERENCES order_procurement_requests(id)
);
