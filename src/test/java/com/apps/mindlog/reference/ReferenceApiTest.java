package com.apps.mindlog.reference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ReferenceRepositoryTest.Database.class)
@WithMockUser
@Transactional
class ReferenceApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test void defaultsAndContextListsMatchTheContractWithoutPageEnvelope() throws Exception {
        mvc.perform(get("/api/situation-types").header("X-API-Version","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(8))
                .andExpect(jsonPath("$.content[0].name").value("발표")).andExpect(jsonPath("$.page").doesNotExist());
        mvc.perform(get("/api/situation-types").param("context","onboarding").header("X-API-Version","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(10));
        mvc.perform(get("/api/body-symptoms").header("X-API-Version","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(8))
                .andExpect(jsonPath("$.content[*].name",not(hasItem("회피"))));
        mvc.perform(get("/api/body-symptoms").param("context","onboarding").header("X-API-Version","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(9));
    }

    @Test void emotionsUseSeparateStableCodeAndId() throws Exception {
        mvc.perform(get("/api/emotion-characters").header("X-API-Version","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(7))
                .andExpect(jsonPath("$.content[1].id").value(2))
                .andExpect(jsonPath("$.content[1].code").value("neutral"))
                .andExpect(jsonPath("$.content[1].name").value("보통"));
    }

    @Test void inactiveChoicesAreExcludedUnlessExplicitlyRequested() throws Exception {
        jdbc.update("UPDATE situation_types SET active=false WHERE id=1");
        mvc.perform(get("/api/situation-types").header("X-API-Version","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(7));
        mvc.perform(get("/api/situation-types").param("includeInactive","true").header("X-API-Version","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(8));
    }

    @Test void unseededCatalogsReturnEmptyLists() throws Exception {
        for (String path : new String[]{"/api/anxiety-patterns","/api/distortion-tags","/api/positive-solutions"}) {
            mvc.perform(get(path).header("X-API-Version","1")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.page").doesNotExist());
        }
    }

    @Test void guideHasTheExactDetailFieldsAndReadDoesNotMutateIt() throws Exception {
        jdbc.update("""
                INSERT INTO distortion_tags(id,name,definition,example_thoughts,self_questions,reframes,display_order,
                    active,created_at,updated_at)
                VALUES(100,'검증용 유형','검증용 정의','["예시"]','["질문"]','["바꿔보기"]',1,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        mvc.perform(get("/api/distortion-tags/100").header("X-API-Version","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.definition").value("검증용 정의"))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.exampleThoughts[0]").value("예시"))
                .andExpect(jsonPath("$.selfQuestions[0]").value("질문"))
                .andExpect(jsonPath("$.reframes[0]").value("바꿔보기"))
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(get("/api/distortion-tags").header("X-API-Version","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].description").value("검증용 정의"));
    }

    @Test void solutionFilterUsesPublishedCategoryNames() throws Exception {
        jdbc.update("""
                INSERT INTO positive_solutions(id,category_code,category_name,title,solution_content,active,
                    display_order,created_at,updated_at)
                VALUES(100,'awareness','알아차리기','검증용 제목','검증용 설명',true,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        mvc.perform(get("/api/positive-solutions").param("category","알아차리기").header("X-API-Version","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].category").value("알아차리기"))
                .andExpect(jsonPath("$.content[0].title").value("검증용 제목"))
                .andExpect(jsonPath("$.content[0].description").value("검증용 설명"));
        mvc.perform(get("/api/positive-solutions").param("category","회복력").header("X-API-Version","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test void invalidQueriesAndAbsentDetailUseProblemDetails() throws Exception {
        for (String path : new String[]{"/api/situation-types?context=after",
                "/api/body-symptoms?context=before","/api/positive-solutions?category=unknown",
                "/api/emotion-characters?includeInactive=garbage"}) {
            mvc.perform(get(path).header("X-API-Version","1")).andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/validation-error"));
        }
        mvc.perform(get("/api/distortion-tags/999").header("X-API-Version","1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/resource-not-found"));
    }

    @Test void versionIsRequired() throws Exception {
        mvc.perform(get("/api/emotion-characters")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/emotion-characters").header("X-API-Version","2")).andExpect(status().isBadRequest());
    }

    @Test @WithAnonymousUser
    void anonymousRequestsAreRejectedBeforeCatalogAccess() throws Exception {
        mvc.perform(get("/api/emotion-characters").header("X-API-Version","1"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }
}
