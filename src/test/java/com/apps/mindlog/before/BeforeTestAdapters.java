package com.apps.mindlog.before;

import com.apps.mindlog.global.security.AccountAccess;
import java.util.Optional;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class BeforeTestAdapters {
    @Bean @ServiceConnection PostgreSQLContainer postgres() { return new PostgreSQLContainer("postgres:16"); }

    @Bean @Primary AccountAccess beforeTestAccountAccess(JdbcTemplate jdbc) {
        return new AccountAccess() {
            @Override public Account current() {
                long id = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
                return read(id, false).orElseThrow();
            }
            @Override public Optional<Account> lock(long id) { return read(id, true); }
            private Optional<Account> read(long id, boolean lock) {
                return jdbc.query("SELECT id,data_generation,onboarding_completed,nickname FROM users WHERE id=? AND account_status='active'"
                                + (lock ? " FOR UPDATE" : ""),
                        (rs, row) -> new Account(rs.getLong("id"), rs.getLong("data_generation"),
                                rs.getBoolean("onboarding_completed"), rs.getString("nickname")), id)
                        .stream().findFirst();
            }
        };
    }

}
