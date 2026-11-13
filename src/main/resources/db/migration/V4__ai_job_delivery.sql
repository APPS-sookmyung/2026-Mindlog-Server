-- Application job delivery is recoverable independently of JobRunr's transaction.
ALTER TABLE ai_jobs ADD COLUMN attempt_token uuid;
ALTER TABLE ai_jobs ADD COLUMN lease_until timestamptz;
ALTER TABLE ai_jobs ADD COLUMN dispatch_after timestamptz;
CREATE INDEX ix_ai_jobs_dispatch ON ai_jobs (dispatch_after, created_at)
    WHERE status IN ('PENDING', 'PROCESSING');
