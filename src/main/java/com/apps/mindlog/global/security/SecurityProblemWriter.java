package com.apps.mindlog.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class SecurityProblemWriter {
    private final ObjectMapper mapper;
    public SecurityProblemWriter(ObjectMapper mapper) { this.mapper = mapper; }

    public void write(HttpServletRequest request, HttpServletResponse response, ProblemDetail problem) throws IOException {
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // Include all common fields even when Jackson would omit the default about:blank type.
        mapper.writeValue(response.getOutputStream(), Map.of(
                "type", problem.getType() == null ? "about:blank" : problem.getType().toString(),
                "title", Objects.requireNonNullElse(problem.getTitle(), "Request failed"),
                "status", problem.getStatus(),
                "detail", Objects.requireNonNullElse(problem.getDetail(), "요청을 처리할 수 없습니다."),
                "instance", problem.getInstance().toString()));
    }
}
