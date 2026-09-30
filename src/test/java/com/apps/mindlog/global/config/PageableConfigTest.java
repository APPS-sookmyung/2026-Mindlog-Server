package com.apps.mindlog.global.config;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PageableConfigTest.Controller.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({PageableConfig.class, PageableConfigTest.Controller.class})
class PageableConfigTest {
    @Autowired MockMvc mvc;

    @ParameterizedTest
    @CsvSource({"'',0,20", "?page=0&size=1,0,1", "?page=2&size=20,2,20",
            "?size=100,0,100", "?size=101,0,100", "?page=-1&size=0,0,20",
            "?page=abc&size=abc,0,20", "?size=-1,0,20"})
    void resolvesPageableAndSerializesStableEnvelope(String query, int page, int size) throws Exception {
        mvc.perform(get("/test/pages" + query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0]").value("record"))
                .andExpect(jsonPath("$.page.number").value(page))
                .andExpect(jsonPath("$.page.size").value(size))
                .andExpect(jsonPath("$.page.totalElements").value(137))
                .andExpect(jsonPath("$.page.totalPages").value((137 + size - 1) / size))
                .andExpect(jsonPath("$.pageable").doesNotExist());
    }

    @RestController
    static class Controller {
        @GetMapping("/test/pages")
        Page<String> list(Pageable pageable) {
            return new PageImpl<>(List.of("record"), pageable, 137);
        }
    }
}
