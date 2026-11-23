-- Infrastructure described by the scaffold; user deletion also removes saved responses.
CREATE TABLE idempotency_records (
    user_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    data_generation bigint NOT NULL CHECK (data_generation >= 0),
    operation varchar(255) NOT NULL,
    request_key varchar(255) NOT NULL,
    request_hash bytea NOT NULL,
    response_status integer,
    response_body text,
    response_headers jsonb,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (user_id, data_generation, operation, request_key),
    CONSTRAINT ck_idempotency_records_response CHECK (
        (response_status IS NULL AND response_body IS NULL AND response_headers IS NULL)
        OR (response_status IS NOT NULL AND response_status BETWEEN 200 AND 299 AND response_body IS NOT NULL AND response_headers IS NOT NULL)
    )
);
