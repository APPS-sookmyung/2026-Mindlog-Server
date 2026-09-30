package com.apps.mindlog.global.security;

import com.apps.mindlog.global.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class ProblemDetailsAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final SecurityProblemWriter writer;
    public ProblemDetailsAuthenticationEntryPoint(SecurityProblemWriter writer) { this.writer = writer; }
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        ErrorType type = ErrorType.INVALID_TOKEN;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(type.getStatus(), "인증이 필요합니다.");
        problem.setType(type.getType());
        problem.setTitle(type.getTitle());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        writer.write(request, response, problem);
    }
}
