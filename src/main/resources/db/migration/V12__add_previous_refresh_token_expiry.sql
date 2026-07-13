alter table auth_tokens
    add column previous_refresh_token_expires_at timestamptz;
