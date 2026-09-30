package com.apps.mindlog.global.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SecurityProblemTest.Controller.class)
@Import({SecurityConfig.class, SecurityProblemWriter.class, ProblemDetailsAuthenticationEntryPoint.class,
        ProblemDetailsAccessDeniedHandler.class, SecurityProblemTest.Restricted.class, SecurityProblemTest.Controller.class})
class SecurityProblemTest {
    @Autowired MockMvc mvc;
    @Autowired ProblemDetailsAuthenticationEntryPoint entry;
    @Autowired ProblemDetailsAccessDeniedHandler denied;
    @Test void handlerMessagesDoNotExposeInternalExceptionsOrQuery() throws Exception {
        var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/private");
        request.setQueryString("token=secret-query");
        var unauthorized = new org.springframework.mock.web.MockHttpServletResponse();
        entry.commence(request, unauthorized, new org.springframework.security.authentication.BadCredentialsException("secret-exception"));
        org.assertj.core.api.Assertions.assertThat(unauthorized.getContentAsString()).doesNotContain("secret-exception", "secret-query");
        var forbidden = new org.springframework.mock.web.MockHttpServletResponse();
        denied.handle(request, forbidden, new org.springframework.security.access.AccessDeniedException("secret-exception"));
        org.assertj.core.api.Assertions.assertThat(forbidden.getContentAsString()).doesNotContain("secret-exception", "secret-query");
    }
    @Test void anonymousRequestUsesProblemAndBearerChallenge() throws Exception {
        mvc.perform(get("/api/private").queryParam("token", "private-secret"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/invalid-token"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/private"))
                .andExpect(content().string(not(containsString("private-secret"))));
    }
    @Test @WithMockUser
    void authenticatedButUnauthorizedUses403Problem() throws Exception {
        mvc.perform(get("/test/restricted")).andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(403));
    }
    @Test @WithMockUser
    void authenticatedRequestReachesController() throws Exception {
        mvc.perform(get("/api/private").header("X-API-Version", "1")).andExpect(status().isOk());
    }
    @Test void publicLoginIsAccessibleButOtherMethodsRemainProtected() throws Exception {
        mvc.perform(post("/api/auth/email-sessions").header("X-API-Version", "1")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/email-sessions")).andExpect(status().isUnauthorized());
    }
    @RestController static class Controller {
        @GetMapping("/api/private") String privateApi() { return "ok"; }
        @PostMapping("/api/auth/email-sessions") String login() { return "ok"; }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class Restricted {
        @Bean @Order(0)
        SecurityFilterChain restricted(HttpSecurity http, ProblemDetailsAuthenticationEntryPoint entry,
                ProblemDetailsAccessDeniedHandler denied) throws Exception {
            return http.securityMatcher("/test/restricted")
                    .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("ADMIN"))
                    .exceptionHandling(errors -> errors.authenticationEntryPoint(entry).accessDeniedHandler(denied)).build();
        }
    }
}
