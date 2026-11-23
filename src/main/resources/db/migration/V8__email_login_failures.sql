-- Stores only an HMAC of the normalized email so unknown addresses can be throttled
-- without retaining the submitted address in plaintext.
CREATE TABLE email_login_attempts (
    email_key bytea PRIMARY KEY,
    failed_login_count int NOT NULL DEFAULT 0,
    failed_login_window_started_at timestamptz,
    login_locked_until timestamptz,
    updated_at timestamptz NOT NULL,
    CONSTRAINT ck_email_login_attempts_count CHECK (failed_login_count >= 0)
);
