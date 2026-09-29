package com.apps.mindlog.global.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApiVersionConfigTest.Controller.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiVersionConfig.class, ApiVersionConfigTest.Controller.class})
class ApiVersionConfigTest {
    @Autowired MockMvc mvc;
    @Test void versionOneWorks() throws Exception {
        mvc.perform(get("/api/test").header("X-API-Version", "1")).andExpect(status().isOk());
    }
    @Test void missingVersionIsProblem() throws Exception {
        mvc.perform(get("/api/test")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/validation-error"));
    }
    @ParameterizedTest @ValueSource(strings = {"2", "bad", ""})
    void unsupportedVersionIsProblem(String version) throws Exception {
        mvc.perform(get("/api/test").header("X-API-Version", version)).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }
    @Test void nonApiRouteNeedsNoHeader() throws Exception {
        mvc.perform(get("/test/docs")).andExpect(status().isOk());
    }
    @RestController static class Controller {
        @GetMapping(value = "/api/test", version = "1") String api() { return "ok"; }
        @GetMapping("/test/docs") String docs() { return "ok"; }
    }
}
