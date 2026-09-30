package com.apps.mindlog.after.repository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** After read model; no access to another domain's repository implementation. */
@Repository
public class RecordInsightHistoryQuery {
    private final JdbcTemplate jdbc;
    public RecordInsightHistoryQuery(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record History(long count,BigDecimal averageExpectedMinusActual){}
    public History previous(long userId,long situationId,long currentAfterId,Instant cutoff){
        return jdbc.queryForObject("""
            SELECT count(*) count,avg(b.expected_score-a.actual_score) difference
            FROM after_logs a JOIN before_logs b ON b.id=a.before_log_id
            WHERE b.user_id=? AND b.situation_type_id=? AND a.id<>?
              AND a.record_status='FINALIZED' AND a.actual_score IS NOT NULL AND a.finalized_at<=?
            """,(rs,n)->new History(rs.getLong("count"),rs.getBigDecimal("difference")),userId,situationId,currentAfterId,Timestamp.from(cutoff));
    }
}
