package com.apps.mindlog.notification.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Clock;

/** Only the final-save integration; notification scheduling/list/push remain separate work. */
@Service
public class AfterNotificationResolver {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public AfterNotificationResolver(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void resolve(long userId,long beforeId){
        jdbc.update("""
            UPDATE notifications SET status='cancelled',updated_at=?
            WHERE user_id=? AND before_log_id=? AND type='after_induce' AND status IN ('pending','shown')
            """,Timestamp.from(clock.instant()),userId,beforeId);
    }
}
