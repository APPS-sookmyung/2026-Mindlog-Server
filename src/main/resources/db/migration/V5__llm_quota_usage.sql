CREATE TABLE llm_quota_usage (
    user_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    bucket_start timestamptz NOT NULL,
    used integer NOT NULL CHECK (used >= 1),
    PRIMARY KEY (user_id)
);
