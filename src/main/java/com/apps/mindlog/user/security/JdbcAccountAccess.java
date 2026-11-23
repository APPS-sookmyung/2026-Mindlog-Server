package com.apps.mindlog.user.security;

import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import com.apps.mindlog.global.security.AccountAccess;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class JdbcAccountAccess implements AccountAccess {
    private final JdbcTemplate jdbc;

    public JdbcAccountAccess(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Account current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Number principal)) throw new ApiException(ErrorType.INVALID_TOKEN);
        return find(principal.longValue(), false).orElseThrow(() -> new ApiException(ErrorType.INVALID_TOKEN));
    }

    @Override
    public Optional<Account> lock(long userId) {
        return find(userId, true);
    }

    private Optional<Account> find(long userId, boolean lock) {
        String sql = "SELECT id,data_generation,onboarding_completed,nickname FROM users WHERE id=? AND account_status<>'deleting'"
                + (lock ? " FOR UPDATE" : "");
        List<Account> accounts = jdbc.query(sql, (rs, row) -> new Account(
                rs.getLong("id"), rs.getLong("data_generation"), rs.getBoolean("onboarding_completed"), rs.getString("nickname")), userId);
        return accounts.stream().findFirst();
    }
}
