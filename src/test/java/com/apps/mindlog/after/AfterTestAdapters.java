package com.apps.mindlog.after;

import com.apps.mindlog.after.port.BeforeAccess;
import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import com.apps.mindlog.global.security.AccountAccess;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Test-only adapters. These do not constitute teammate auth/User/Before production implementations. */
@TestConfiguration(proxyBeanMethods=false)
public class AfterTestAdapters {
    @Bean @ServiceConnection PostgreSQLContainer postgres(){return new PostgreSQLContainer("postgres:16");}
    @Bean AccountAccess testAccountAccess(JdbcTemplate jdbc){return new AccountAccess(){
        public Account current(){
            long id=Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
            return read(id,false).orElseThrow(()->new ApiException(ErrorType.INVALID_TOKEN));
        }
        public Optional<Account> lock(long id){return read(id,true);}
        Optional<Account> read(long id,boolean lock){return jdbc.query(
                "SELECT id,data_generation,onboarding_completed,nickname FROM users WHERE id=? AND account_status='active'"+(lock?" FOR UPDATE":""),
                (rs,n)->new Account(rs.getLong("id"),rs.getLong("data_generation"),rs.getBoolean("onboarding_completed"),rs.getString("nickname")),id).stream().findFirst();}
    };}
    @Bean BeforeAccess testBeforeAccess(JdbcTemplate jdbc){return new BeforeAccess(){
        public Optional<Snapshot> findOwned(long id,long user){return read(id,user,false);}
        public Optional<Snapshot> lockOwned(long id,long user){return read(id,user,true);}
        Optional<Snapshot> read(long id,long user,boolean lock){return jdbc.query("""
            SELECT b.*,s.name situation_name FROM before_logs b JOIN situation_types s ON s.id=b.situation_type_id
            WHERE b.id=? AND b.user_id=?
            """+(lock?" FOR UPDATE OF b":""),(rs,n)->new Snapshot(rs.getLong("id"),rs.getLong("user_id"),
                    rs.getLong("situation_type_id"),rs.getString("situation_name"),rs.getObject("scheduled_at",LocalDate.class),
                    rs.getInt("expected_score"),rs.getString("expected_score_comment"),rs.getString("worst_scenario"),
                    rs.getString("title"),rs.getString("description")),id,user).stream().findFirst();}
    };}
}
