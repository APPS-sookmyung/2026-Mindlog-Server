package com.apps.mindlog.global.idempotency;

import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.util.UrlPathHelper;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class IdempotencyKeyFilter extends OncePerRequestFilter {
    public static final String HEADER = "Idempotency-Key";
    public static final String ATTRIBUTE = IdempotencyKeyFilter.class.getName() + ".key";
    private final HandlerExceptionResolver errors;

    public IdempotencyKeyFilter(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver errors) {
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        return !path.startsWith("/api/") || Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            var headers = Collections.list(request.getHeaders(HEADER));
            if (headers.size() > 1) {
                throw new ApiException(ErrorType.VALIDATION_ERROR, "Idempotency-Key 헤더를 확인해 주세요.");
            }
            if (!headers.isEmpty()) {
                String key = headers.getFirst();
                IdempotencyService.validateKey(key);
                request.setAttribute(ATTRIBUTE, key);
            }
        } catch (ApiException exception) {
            if (errors.resolveException(request, response, null, exception) == null) throw exception;
            return;
        }
        chain.doFilter(request, response);
    }

    /** Used only by endpoints whose API contract requires the header. */
    public static String requireKey(HttpServletRequest request) {
        String key = (String) request.getAttribute(ATTRIBUTE);
        IdempotencyService.validateKey(key);
        return key;
    }
}
