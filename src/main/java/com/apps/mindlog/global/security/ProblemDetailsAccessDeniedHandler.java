package com.apps.mindlog.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Framework authorization failures only. Domain ownership violations remain 404. */
@Component
public class ProblemDetailsAccessDeniedHandler implements AccessDeniedHandler {
    private final SecurityProblemWriter writer;
    public ProblemDetailsAccessDeniedHandler(SecurityProblemWriter writer) { this.writer = writer; }
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception) throws IOException {
        writer.write(request, response, ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."));
    }
}
