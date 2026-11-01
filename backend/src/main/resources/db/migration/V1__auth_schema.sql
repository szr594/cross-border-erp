CREATE TABLE roles (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE app_users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    login_name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_count INTEGER NOT NULL DEFAULT 0,
    locked_until DATETIME(6),
    last_login_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_app_users_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES app_users(id),
    role_id BIGINT NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE refresh_sessions (
    id BINARY(16) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6),
    replaced_by_session_id BINARY(16),
    created_at DATETIME(6) NOT NULL,
    last_used_at DATETIME(6),
    ip_address VARCHAR(64),
    user_agent VARCHAR(512)
);

CREATE INDEX ix_refresh_sessions_user_id ON refresh_sessions(user_id);
CREATE INDEX ix_refresh_sessions_expires_at ON refresh_sessions(expires_at);

INSERT INTO roles(code, name) VALUES
    ('OWNER', '老板'),
    ('OPERATIONS', '运营'),
    ('PROCUREMENT', '采购'),
    ('WAREHOUSE', '仓库'),
    ('PACKING', '打包');
