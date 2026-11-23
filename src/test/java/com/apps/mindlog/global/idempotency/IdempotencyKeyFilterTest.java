package com.apps.mindlog.global.idempotency;

import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;

import static org.assertj.core.api.Assertions.*;

class IdempotencyKeyFilterTest {
    @Test void validatesHeaderWithoutRequiringItForEveryEndpoint() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/users");
        var response = new MockHttpServletResponse();
        var continued = new AtomicBoolean();
        var filter = filter();
        filter.doFilter(request, response, (req, res) -> continued.set(true));
        assertThat(continued).isTrue();
        assertThatThrownBy(() -> IdempotencyKeyFilter.requireKey(request)).isInstanceOf(ApiException.class);
    }

    @Test void exposesValidHeaderToAnOptedInEndpoint() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/after-logs");
        request.addHeader("Idempotency-Key", "123e4567-e89b-12d3-a456-426614174000");
        filter().doFilter(request, new MockHttpServletResponse(), (req, res) -> { });
        assertThat(IdempotencyKeyFilter.requireKey(request)).isEqualTo(request.getHeader("Idempotency-Key"));
    }

    @Test void rejectsEmptyWhitespaceOversizedAndDuplicateHeaders() throws Exception {
        for (String key : new String[]{"", " ", " key", "key ", "한글", "x".repeat(256)}) {
            var request = new MockHttpServletRequest("POST", "/api/after-logs");
            request.addHeader("Idempotency-Key", key);
            var response = new MockHttpServletResponse();
            filter().doFilter(request, response, (req, res) -> { throw new AssertionError("Must not reach endpoint"); });
            assertThat(response.getStatus()).isEqualTo(400);
        }
        var request = new MockHttpServletRequest("POST", "/api/after-logs");
        request.addHeader("Idempotency-Key", "one");
        request.addHeader("Idempotency-Key", "two");
        var response = new MockHttpServletResponse();
        filter().doFilter(request, response, (req, res) -> { throw new AssertionError(); });
        assertThat(response.getStatus()).isEqualTo(400);
    }

    private IdempotencyKeyFilter filter() {
        return new IdempotencyKeyFilter((request, response, handler, exception) -> {
            assertThat(exception).isInstanceOfSatisfying(ApiException.class,
                    failure -> assertThat(failure.getErrorType()).isEqualTo(ErrorType.VALIDATION_ERROR));
            response.setStatus(400);
            return new ModelAndView();
        });
    }
}
