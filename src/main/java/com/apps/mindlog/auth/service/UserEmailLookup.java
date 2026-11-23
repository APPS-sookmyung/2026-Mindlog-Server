package com.apps.mindlog.auth.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read-only lookup against the users table; Google accounts share this email column. */
@Repository
public class UserEmailLookup {
    private final JdbcTemplate jdbc;

    public UserEmailLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean existsByEmailIgnoreCase(String normalizedEmail) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM users WHERE lower(email) = lower(?))",
                Boolean.class, normalizedEmail);
        return Boolean.TRUE.equals(exists);
    }
}
