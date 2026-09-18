package com.apps.mindlog.global.error;

import com.apps.mindlog.global.error.exception.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @EnumSource(ErrorType.class)
    void domainErrorsUseProblemDetails(ErrorType type) throws Exception {
        mvc.perform(get("/test/errors/{type}", type.name()).queryParam("private", "secret"))
                .andExpect(status().is(type.getStatus().value()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(type.getType().toString()))
                .andExpect(jsonPath("$.title").value(type.getTitle()))
                .andExpect(jsonPath("$.status").value(type.getStatus().value()))
                .andExpect(jsonPath("$.instance").value("/test/errors/" + type.name()))
                .andExpect(jsonPath("$.detail").value(type == ErrorType.INTERNAL_ERROR
                        ? "요청을 처리하는 중 오류가 발생했습니다." : "공개 가능한 설명"))
                .andExpect(content().string(not(containsString("secret"))));
    }

    @Test
    void validationErrorsExposeFieldsAndStableReasonsWithoutRejectedValues() throws Exception {
        mvc.perform(post("/test/logs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedScore\":150,\"worstScenario\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(ErrorType.VALIDATION_ERROR.getType().toString()))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].field").value("expectedScore"))
                .andExpect(jsonPath("$.errors[0].reason").value("range"))
                .andExpect(jsonPath("$.errors[1].field").value("worstScenario"))
                .andExpect(jsonPath("$.errors[1].reason").value("required"))
                .andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist());
    }

    @Test
    void malformedJsonIsValidationErrorWithoutParserDetails() throws Exception {
        mvc.perform(post("/test/logs").contentType(MediaType.APPLICATION_JSON).content("{private-diary"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(ErrorType.VALIDATION_ERROR.getType().toString()))
                .andExpect(content().string(not(containsString("private-diary"))));
    }

    @Test
    void wrongPathVariableTypeIsBadRequest() throws Exception {
        mvc.perform(get("/test/numbers/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(ErrorType.VALIDATION_ERROR.getType().toString()));
    }

    @Test
    void unexpectedFailureDoesNotExposeInternalMessage() throws Exception {
        mvc.perform(get("/test/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(ErrorType.INTERNAL_ERROR.getType().toString()))
                .andExpect(jsonPath("$.instance").value("/test/failure"))
                .andExpect(content().string(not(containsString("sensitive database detail"))));
    }

    @Test
    void unsupportedMethodPreservesStatusAndAllowHeader() throws Exception {
        mvc.perform(get("/test/logs"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void unsupportedContentTypeIsNotInternalError() throws Exception {
        mvc.perform(post("/test/logs").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void validBoundaryRequestIsNotIntercepted() throws Exception {
        mvc.perform(post("/test/logs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedScore\":0,\"worstScenario\":\"생각\"}"))
                .andExpect(status().isOk());
    }

    @RestController
    static class TestController {
        @GetMapping("/test/errors/{type}")
        void fail(@PathVariable ErrorType type) {
            throw new ApiException(type, "공개 가능한 설명");
        }

        @PostMapping("/test/logs")
        void create(@Valid @RequestBody LogRequest request) { }

        @GetMapping("/test/numbers/{id}")
        void number(@PathVariable Long id) { }

        @GetMapping("/test/failure")
        void unexpected() {
            throw new IllegalStateException("sensitive database detail");
        }
    }

    record LogRequest(@NotNull @Min(0) @Max(100) Integer expectedScore,
                      @NotBlank String worstScenario) { }
}
