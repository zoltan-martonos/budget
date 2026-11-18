CREATE TABLE mcp_access_tokens (
    user_id BIGINT PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    CONSTRAINT fk_mcp_access_tokens_user
        FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE CASCADE
);
