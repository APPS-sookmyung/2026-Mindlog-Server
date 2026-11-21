CREATE TABLE request_rate_limits (
    client_key varchar(64) PRIMARY KEY,
    window_started_at timestamptz NOT NULL,
    request_count integer NOT NULL CHECK (request_count >= 0)
);
