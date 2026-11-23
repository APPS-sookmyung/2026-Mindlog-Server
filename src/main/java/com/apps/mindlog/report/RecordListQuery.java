package com.apps.mindlog.report;

import com.apps.mindlog.global.config.TimeConfig;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Internal read model for the authenticated owner's home/report. No HTTP endpoint is added here. */
@Repository
public class RecordListQuery {
    private final JdbcTemplate jdbc;
    public RecordListQuery(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record RecordSummary(long beforeLogId, Long afterLogId, String recordStatus, String title,
                                String situationTypeName, LocalDate date, int expectedScore, Integer actualScore) {}

    public List<RecordSummary> recent(long userId) {
        return jdbc.query("""
                SELECT b.id before_id, a.id after_id, b.title, s.name situation_name, b.created_at,
                       b.expected_score,
                       CASE WHEN a.record_status = 'FINALIZED' THEN 'FINALIZED' ELSE 'IN_PROGRESS' END status,
                       CASE WHEN a.record_status = 'FINALIZED' THEN a.actual_score ELSE NULL END actual_score
                FROM before_logs b JOIN situation_types s ON s.id = b.situation_type_id
                LEFT JOIN after_logs a ON a.before_log_id = b.id
                WHERE b.user_id = ?
                ORDER BY b.created_at DESC, b.id DESC LIMIT 3
                """, (rs, row) -> new RecordSummary(rs.getLong("before_id"), rs.getObject("after_id", Long.class),
                rs.getString("status"), rs.getString("title"), rs.getString("situation_name"),
                rs.getTimestamp("created_at").toInstant().atZone(TimeConfig.SEOUL).toLocalDate(),
                rs.getInt("expected_score"), rs.getObject("actual_score", Integer.class)), userId);
    }
}
