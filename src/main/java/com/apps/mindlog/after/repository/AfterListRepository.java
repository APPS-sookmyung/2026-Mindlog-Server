package com.apps.mindlog.after.repository;

import com.apps.mindlog.after.dto.response.AfterListResponse;
import com.apps.mindlog.after.entity.*;
import com.apps.mindlog.global.config.TimeConfig;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Owned read model: bounded list and count, no cross-domain Repository calls. */
@Repository
public class AfterListRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public AfterListRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
    public Page<AfterListResponse> find(long userId,Instant from,Instant until,Long situation,Pageable page){
        var args=new HashMap<String,Object>();args.put("user",userId);
        String where=" WHERE b.user_id=:user";
        if(from!=null){where+=" AND a.created_at>=:from";args.put("from",Timestamp.from(from));}
        if(until!=null){where+=" AND a.created_at<:until";args.put("until",Timestamp.from(until));}
        if(situation!=null){where+=" AND b.situation_type_id=:situation";args.put("situation",situation);}
        String joins=" FROM after_logs a JOIN before_logs b ON b.id=a.before_log_id JOIN situation_types s ON s.id=b.situation_type_id LEFT JOIN af_ai_feedbacks f ON f.after_log_id=a.id";
        Long total=jdbc.queryForObject("SELECT count(*)"+joins+where,args,Long.class);
        args.put("limit",page.getPageSize());args.put("offset",page.getOffset());
        var rows=jdbc.query("""
            SELECT a.*,b.situation_type_id,b.expected_score,s.name situation_name,
                CASE WHEN f.input_version<>a.input_version THEN 'INVALIDATED'
                     ELSE COALESCE(f.analysis_status,'NOT_REQUESTED') END current_analysis
            """+joins+where+" ORDER BY a.created_at DESC,a.id DESC LIMIT :limit OFFSET :offset",args,(rs,n)->{
                var state=RecordStatus.valueOf(rs.getString("record_status"));
                return new AfterListResponse(rs.getLong("id"),rs.getLong("before_log_id"),rs.getLong("situation_type_id"),
                        rs.getString("situation_name"),rs.getInt("expected_score"),state==RecordStatus.FINALIZED?rs.getObject("actual_score",Integer.class):null,
                        state,rs.getLong("input_version"),AnalysisStatus.valueOf(rs.getString("current_analysis")),
                        state==RecordStatus.FINALIZED,rs.getTimestamp("created_at").toInstant().atZone(TimeConfig.SEOUL).toOffsetDateTime());
            });
        return new PageImpl<>(rows,page,total==null?0:total);
    }
}
