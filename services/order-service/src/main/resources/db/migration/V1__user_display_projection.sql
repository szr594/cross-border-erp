CREATE TABLE user_display_projections (
    id BIGINT NOT NULL PRIMARY KEY,
    login_name VARCHAR(100) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    source_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_user_projection_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ck_user_projection_version CHECK (source_version >= 0)
);
