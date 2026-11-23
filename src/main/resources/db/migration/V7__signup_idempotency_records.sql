CREATE TABLE signup_idempotency_records (
    request_key varchar(255) PRIMARY KEY,
    request_hash bytea NOT NULL,
    user_id bigint REFERENCES users(id) ON DELETE CASCADE,
    response_status integer,
    response_body bytea,
    response_headers jsonb,
    created_at timestamptz NOT NULL,
    CONSTRAINT ck_signup_idempotency_result CHECK (
        (user_id IS NULL AND response_status IS NULL AND response_body IS NULL AND response_headers IS NULL)
        OR (user_id IS NOT NULL AND response_status = 201 AND response_body IS NOT NULL AND response_headers IS NOT NULL)
    )
);
