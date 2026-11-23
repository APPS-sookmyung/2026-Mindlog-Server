package com.apps.mindlog.global.error;

import com.apps.mindlog.global.error.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RetryAfterTest {
    @Test void exposesRetryAfterWithProblemDetails() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new Controller()).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/test/limited")).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/login-rate-limited"));
    }
    @Test void rejectsMissingOrInvalidRetryMetadata() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ApiException(ErrorType.LOGIN_RATE_LIMITED));
        assertThatIllegalArgumentException().isThrownBy(() -> new ApiException(ErrorType.LOGIN_RATE_LIMITED, "wait", -1));
        assertThatIllegalArgumentException().isThrownBy(() -> new ApiException(ErrorType.INVALID_TOKEN, "wait", 1));
        assertThat(new ApiException(ErrorType.LOGIN_RATE_LIMITED, "wait", 0).getRetryAfterSeconds()).hasValue(0);
    }
    @RestController static class Controller {
        @GetMapping("/test/limited") void limited() { throw new ApiException(ErrorType.LOGIN_RATE_LIMITED, "잠시 후 다시 시도해 주세요.", 900); }
    }
}
