package com.apps.mindlog.reference;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
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
    @Autowired EntityManager entities;

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

    @Test void reviewedGuidesAreAvailableAndAnxietyPatternsRemainReserved() throws Exception {
        mvc.perform(get("/api/anxiety-patterns").header("X-API-Version","1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.page").doesNotExist());
        mvc.perform(get("/api/distortion-tags").header("X-API-Version","1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(12))
                .andExpect(jsonPath("$.content[5].name").value("마음 읽기"))
                .andExpect(jsonPath("$.content[10].name").value("파국화"));
        mvc.perform(get("/api/distortion-tags/6").header("X-API-Version","1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.illustrationKey").value("distortion-mind-reading"))
                .andExpect(jsonPath("$.exampleThoughts[0]").value(containsString("답장이 늦는다는 이유")))
                .andExpect(jsonPath("$.selfQuestions[0]").isString())
                .andExpect(jsonPath("$.reframes[0]").isString());
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
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[*].description", hasItem("검증용 정의")));
    }

    @Test void catastrophizingReturnsItsConfirmedExampleQuestionAndReframe() throws Exception {
        mvc.perform(get("/api/distortion-tags/11").header("X-API-Version", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("파국화"))
                .andExpect(jsonPath("$.exampleThoughts[0]").value("발표에서 한 번 말이 막히면 발표를 전부 망칠 거야."))
                .andExpect(jsonPath("$.selfQuestions[0]").value("잠깐 말이 막히면 실제로 어떤 일이 생길 수 있을까요?"))
                .andExpect(jsonPath("$.reframes[0]").value("잠깐 멈추더라도 메모를 보고 다음 문장부터 이어갈 수 있어요."));
    }

    @Test void solutionFilterUsesPublishedCategoryNames() throws Exception {
        jdbc.update("""
                INSERT INTO positive_solutions(id,category_code,category_name,title,solution_content,active,
                    display_order,created_at,updated_at)
                VALUES(100,'awareness','알아차리기','검증용 제목','검증용 설명',true,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO positive_solutions(id,situation_type_id,category_code,category_name,title,solution_content,
                    active,display_order,created_at,updated_at)
                VALUES(101,1,'awareness','알아차리기','발표 전용','이전 상황 카드',true,1,now(),now())
                """);
        mvc.perform(get("/api/positive-solutions").param("category","알아차리기").header("X-API-Version","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].category").value("알아차리기"))
                .andExpect(jsonPath("$.content[0].title").value("검증용 제목"))
                .andExpect(jsonPath("$.content[0].description").value("검증용 설명"));
        mvc.perform(get("/api/positive-solutions").param("category","회복력").header("X-API-Version","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[*].title",
                        contains("다시 일어서는 힘", "조용한 명상", "충분한 휴식과 숙면")))
                .andExpect(jsonPath("$.content[0].id").value(7));
    }

    @ParameterizedTest
    @ValueSource(strings={"/api/situation-types", "/api/body-symptoms", "/api/emotion-characters",
            "/api/anxiety-patterns", "/api/distortion-tags", "/api/distortion-tags/6", "/api/positive-solutions"})
    void unchangedCatalogsReturn304AndRetainCacheHeaders(String path) throws Exception {
        var response = mvc.perform(get(path).header("X-API-Version", "1"))
                .andExpect(status().isOk()).andReturn().getResponse();
        var tag = response.getHeader(HttpHeaders.ETAG);
        assertThat(tag).isNotBlank();
        var revalidated = mvc.perform(get(path).header("X-API-Version", "1").header(HttpHeaders.IF_NONE_MATCH, tag))
                .andExpect(status().isNotModified()).andExpect(content().string(""))
                .andExpect(header().string(HttpHeaders.ETAG, tag)).andReturn().getResponse();
        for (var result : java.util.List.of(response, revalidated)) {
            assertThat(result.getHeader(HttpHeaders.CACHE_CONTROL)).contains("public", "max-age=3600");
            assertThat(String.join(",", result.getHeaders(HttpHeaders.VARY))).contains("Authorization", "X-API-Version", "Accept");
        }
    }

    @Test void cacheValidatorReflectsContextAndChangedGuideCopy() throws Exception {
        var initial = mvc.perform(get("/api/situation-types").header("X-API-Version", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        mvc.perform(get("/api/situation-types").param("context", "onboarding").header("X-API-Version", "1")
                        .header(HttpHeaders.IF_NONE_MATCH, initial))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ETAG, not(equalTo(initial))))
                .andExpect(jsonPath("$.content.length()").value(10));
        var guide = mvc.perform(get("/api/distortion-tags/6").header("X-API-Version", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        jdbc.update("UPDATE distortion_tags SET self_questions='[\"새 질문\"]' WHERE id=6");
        entities.clear();
        mvc.perform(get("/api/distortion-tags/6").header("X-API-Version", "1").header(HttpHeaders.IF_NONE_MATCH, guide))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ETAG, not(equalTo(guide))))
                .andExpect(jsonPath("$.selfQuestions[0]").value("새 질문"));
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
        mvc.perform(get("/api/emotion-characters").header("X-API-Version","2").header(HttpHeaders.IF_NONE_MATCH, "*"))
                .andExpect(status().isBadRequest());
    }

    @Test @WithAnonymousUser
    void anonymousRequestsAreRejectedBeforeCatalogAccess() throws Exception {
        mvc.perform(get("/api/emotion-characters").header("X-API-Version","1").header(HttpHeaders.IF_NONE_MATCH, "*"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }
}
