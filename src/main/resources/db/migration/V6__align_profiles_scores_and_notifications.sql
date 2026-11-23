-- Age is evaluated in the year of registration (Asia/Seoul), including for V5 accounts.
-- Missing profile data must be supplied from verified information, never inferred.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM users
               WHERE gender IS NULL OR birth_year IS NULL OR birth_year NOT BETWEEN 1900
                     AND EXTRACT(YEAR FROM created_at AT TIME ZONE 'Asia/Seoul')::int - 14) THEN
        RAISE EXCEPTION 'V6: verified gender and eligible birth_year are required for existing users'
            USING HINT = 'See docs/database.md for the V5 profile preflight. Correct affected rows and retry.';
    END IF;
END $$;

ALTER TABLE users
    ALTER COLUMN gender SET NOT NULL,
    ALTER COLUMN birth_year SET NOT NULL,
    ADD CONSTRAINT ck_users_birth_year_at_signup CHECK (
        birth_year BETWEEN 1900 AND EXTRACT(YEAR FROM created_at AT TIME ZONE 'Asia/Seoul')::int - 14
    );

ALTER TABLE before_logs DROP COLUMN expected_score_comment;
ALTER TABLE after_logs DROP COLUMN actual_score_comment;

-- Calendar reminders now live on the device. FK cascades remove only their delivery history.
DELETE FROM notifications WHERE type = 'before_induce';

ALTER TABLE notifications
    DROP CONSTRAINT ck_notifications_target,
    DROP CONSTRAINT ck_notifications_trigger_keyword,
    DROP CONSTRAINT ck_notifications_type,
    DROP COLUMN trigger_keyword,
    DROP COLUMN trigger_at,
    ALTER COLUMN before_log_id SET NOT NULL,
    ADD CONSTRAINT ck_notifications_type CHECK (type = 'after_induce');
