package com.apps.mindlog.global.security;

import com.apps.mindlog.global.error.ErrorType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AccessTokenAuthenticationFilter extends OncePerRequestFilter {
    private final AccessTokenCodec tokens;
    private final JdbcTemplate jdbc;
    private final SecurityProblemWriter problems;
    private final Clock clock;

    public AccessTokenAuthenticationFilter(AccessTokenCodec tokens, JdbcTemplate jdbc, SecurityProblemWriter problems, Clock clock) {
        this.tokens = tokens; this.jdbc = jdbc; this.problems = problems; this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null) { chain.doFilter(request, response); return; }
        if (!authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            write(request, response, ErrorType.INVALID_TOKEN); return;
        }
        AccessTokenCodec.VerifiedToken verified;
        try { verified = tokens.verify(authorization.substring(7), clock); }
        catch (AccessTokenCodec.InvalidAccessTokenException ex) {
            write(request, response, ex.expired() ? ErrorType.EXPIRED_TOKEN : ErrorType.INVALID_TOKEN);
            return;
        }
        boolean active = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM users WHERE id=? AND account_status<>'deleting')", Boolean.class, verified.userId()));
        if (!active) { write(request, response, ErrorType.INVALID_TOKEN); return; }
        var authentication = new UsernamePasswordAuthenticationToken(verified.userId(), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }

    private void write(HttpServletRequest request, HttpServletResponse response, ErrorType type) throws IOException {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(type.getStatus(),
                type == ErrorType.EXPIRED_TOKEN ? "Access Token이 만료되었습니다." : "Access Token 검증에 실패했습니다.");
        body.setType(type.getType()); body.setTitle(type.getTitle()); body.setInstance(URI.create(request.getRequestURI()));
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        problems.write(request, response, body);
    }
}
