package com.apps.mindlog.reference;

import com.apps.mindlog.global.config.JpaConfig;
import com.apps.mindlog.global.config.TimeConfig;
import com.apps.mindlog.reference.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, TimeConfig.class, ReferenceRepositoryTest.Database.class})
class ReferenceRepositoryTest {
    @Autowired EmotionCharacterRepository emotions;
    @Autowired SituationTypeRepository situations;
    @Autowired BodySymptomRepository symptoms;
    @Autowired DistortionTagRepository distortions;
    @Autowired PositiveSolutionRepository solutions;
    @Autowired AnxietyPatternRepository patterns;
    @Autowired JdbcTemplate jdbc;

    @Test void mapsSeededCatalogFlagsCodesAndAuditTimes() {
        assertThat(emotions.findAllByOrderByDisplayOrderAscIdAsc()).hasSize(7);
        assertThat(emotions.findById(2L).orElseThrow().getCode()).isEqualTo("neutral");
        assertThat(emotions.findById(2L).orElseThrow().getCreatedAt()).isNotNull();
        assertThat(situations.findById(2L).orElseThrow().isBeforeSelectable()).isFalse();
        assertThat(situations.findById(2L).orElseThrow().isOnboardingSelectable()).isTrue();
        var avoidance = symptoms.findById(8L).orElseThrow();
        assertThat(avoidance.isAfterSelectable()).isFalse();
        assertThat(avoidance.getSymptomType()).isEqualTo("behavioral");
        assertThat(avoidance.getDescription()).isNull();
    }

    @Test void jsonGuideFieldsRoundTripAsImmutableStringLists() {
        jdbc.update("""
                INSERT INTO distortion_tags(id,name,definition,example_thoughts,self_questions,reframes,display_order,
                    active,created_at,updated_at)
                VALUES(100,'test-guide','test-definition','["생각","두 번째"]','["질문"]','["다르게 보기"]',1,
                    false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        var guide = distortions.findById(100L).orElseThrow();
        assertThat(guide.getExampleThoughts()).containsExactly("생각","두 번째");
        assertThat(guide.getSelfQuestions()).containsExactly("질문");
        assertThat(guide.getReframes()).containsExactly("다르게 보기");
        assertThat(guide.getThemeColor()).isNull();
        assertThat(guide.getIllustrationKey()).isNull();
        assertThat(guide.isActive()).isFalse();
        assertThatThrownBy(() -> guide.getExampleThoughts().add("changed")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void nullableSituationAndReservedPatternMapCorrectly() {
        jdbc.update("""
                INSERT INTO positive_solutions(id,category_code,category_name,title,solution_content,active,
                    display_order,created_at,updated_at)
                VALUES(100,'awareness','test-category','test-title','test-content',false,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO anxiety_patterns(id,code,name,active,display_order,created_at,updated_at)
                VALUES(100,'test-only','test-pattern',false,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        var card = solutions.findById(100L).orElseThrow();
        assertThat(card.getSituationTypeId()).isNull();
        assertThat(card.getCategoryCode()).isEqualTo("awareness");
        assertThat(card.getSolutionContent()).isEqualTo("test-content");
        assertThat(patterns.findById(100L).orElseThrow().getDescription()).isNull();
    }

    @Test void orderHasIdTieBreaker() {
        jdbc.update("UPDATE situation_types SET display_order=1 WHERE id=3");
        assertThat(situations.findAllByOrderByDisplayOrderAscIdAsc()).extracting(value -> value.getId())
                .startsWith(1L,3L,2L);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Database {
        @Bean @ServiceConnection PostgreSQLContainer postgres() { return new PostgreSQLContainer("postgres:16-alpine"); }
    }
}
