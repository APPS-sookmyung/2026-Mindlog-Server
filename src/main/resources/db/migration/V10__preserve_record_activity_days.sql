-- A mutable updated_at cannot preserve all draft-save days for the streak contract (API 2-10 / 6-1).
-- Tie days to Before so account reset/deletion removes activity through the existing cascade.
CREATE TABLE record_activity_days (
    before_log_id bigint NOT NULL REFERENCES before_logs(id) ON DELETE CASCADE,
    activity_date date NOT NULL,
    PRIMARY KEY (before_log_id, activity_date)
);

-- Only dates still represented by existing timestamps can be reconstructed for old records.
INSERT INTO record_activity_days(before_log_id, activity_date)
SELECT id, (created_at AT TIME ZONE 'Asia/Seoul')::date FROM before_logs
UNION SELECT id, (updated_at AT TIME ZONE 'Asia/Seoul')::date FROM before_logs
UNION SELECT before_log_id, (created_at AT TIME ZONE 'Asia/Seoul')::date FROM after_logs
UNION SELECT before_log_id, (updated_at AT TIME ZONE 'Asia/Seoul')::date FROM after_logs
UNION SELECT before_log_id, (finalized_at AT TIME ZONE 'Asia/Seoul')::date
      FROM after_logs WHERE finalized_at IS NOT NULL;

CREATE FUNCTION preserve_before_activity_day() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO record_activity_days VALUES (NEW.id, (NEW.created_at AT TIME ZONE 'Asia/Seoul')::date)
        ON CONFLICT DO NOTHING;
    ELSIF ROW(NEW.title, NEW.description, NEW.situation_type_id, NEW.scheduled_at, NEW.expected_score, NEW.worst_scenario)
       IS DISTINCT FROM ROW(OLD.title, OLD.description, OLD.situation_type_id, OLD.scheduled_at, OLD.expected_score, OLD.worst_scenario) THEN
        INSERT INTO record_activity_days VALUES (NEW.id, (NEW.updated_at AT TIME ZONE 'Asia/Seoul')::date)
        ON CONFLICT DO NOTHING;
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION preserve_after_activity_day() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO record_activity_days VALUES (NEW.before_log_id, (NEW.created_at AT TIME ZONE 'Asia/Seoul')::date)
        ON CONFLICT DO NOTHING;
    ELSIF ROW(NEW.input_version, NEW.free_writing, NEW.record_status, NEW.final_diary, NEW.actual_score)
       IS DISTINCT FROM ROW(OLD.input_version, OLD.free_writing, OLD.record_status, OLD.final_diary, OLD.actual_score) THEN
        INSERT INTO record_activity_days VALUES (NEW.before_log_id, (NEW.updated_at AT TIME ZONE 'Asia/Seoul')::date)
        ON CONFLICT DO NOTHING;
    END IF;
    IF NEW.record_status = 'FINALIZED' AND NEW.finalized_at IS NOT NULL THEN
        INSERT INTO record_activity_days VALUES (NEW.before_log_id, (NEW.finalized_at AT TIME ZONE 'Asia/Seoul')::date)
        ON CONFLICT DO NOTHING;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER before_record_activity AFTER INSERT OR UPDATE ON before_logs
FOR EACH ROW EXECUTE FUNCTION preserve_before_activity_day();
CREATE TRIGGER after_record_activity AFTER INSERT OR UPDATE ON after_logs
FOR EACH ROW EXECUTE FUNCTION preserve_after_activity_day();
