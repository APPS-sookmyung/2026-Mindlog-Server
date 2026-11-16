package com.apps.mindlog.global.db;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ReferenceSeedTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    static Flyway flyway;

    @BeforeAll static void migrate() {
        flyway = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).load();
        flyway.migrate();
    }

    @Test void emotionCodesAndNamesMatchThePublishedContract() throws Exception {
        assertThat(values("SELECT id || ':' || code || ':' || name FROM emotion_characters WHERE active ORDER BY display_order,id"))
                .containsExactly("1:anger:화남","2:neutral:보통","3:happiness:행복","4:sadness:슬픔",
                        "5:surprise:놀람","6:embarrassment:창피함","7:stress:스트레스");
    }

    @Test void situationContextsRemainDistinct() throws Exception {
        assertThat(values("SELECT name FROM situation_types WHERE active AND onboarding_selectable ORDER BY display_order,id"))
                .containsExactly("발표","팀플","시험","교수님 면담","인간관계","건강","업무","돈","미래·진로","기타");
        assertThat(values("SELECT name FROM situation_types WHERE active AND before_selectable ORDER BY display_order,id"))
                .containsExactly("발표","시험","인간관계","건강","업무","돈","미래·진로","기타");
    }

    @Test void symptomContextsExcludeAvoidanceFromAfterOnly() throws Exception {
        assertThat(values("SELECT name FROM body_symptoms WHERE active AND onboarding_selectable ORDER BY display_order,id"))
                .containsExactly("손 떨림","식은땀","어지러움","복통","긴장","목소리 떨림","심박수 증가","회피","기타");
        assertThat(values("SELECT name FROM body_symptoms WHERE active AND after_selectable ORDER BY display_order,id"))
                .containsExactly("손 떨림","식은땀","어지러움","복통","긴장","목소리 떨림","심박수 증가","기타");
        assertThat(values("SELECT name || ':' || symptom_type FROM body_symptoms WHERE name IN ('회피','기타') ORDER BY id"))
                .containsExactly("회피:behavioral","기타:other");
    }

    @Test void reapplyingDoesNotDuplicateOrRewriteMasterData() throws Exception {
        var before = values("SELECT id || ':' || updated_at::text FROM situation_types ORDER BY id");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        flyway.validate();
        assertThat(values("SELECT id || ':' || updated_at::text FROM situation_types ORDER BY id")).isEqualTo(before);
    }

    @Test void generatedIdsDoNotCollideWithFixedSeedIds() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try (var result = statement.executeQuery("""
                    INSERT INTO situation_types(name,onboarding_selectable,before_selectable,display_order,active,created_at,updated_at)
                    VALUES('test-only',false,false,99,false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) RETURNING id
                    """)) {
                result.next();
                assertThat(result.getLong(1)).isGreaterThan(10);
            } finally { connection.rollback(); }
        }
    }

    @Test void guideNamesAndDefinitionsFollowTheFinalCatalog() throws Exception {
        assertThat(values("SELECT name FROM distortion_tags WHERE active ORDER BY display_order,id"))
                .containsExactly("개인화","과잉 일반화","긍정적 측면 묵살","낙인찍기","당위적 사고","마음 읽기",
                        "비난하기","예언적 사고","정서적 추론","정신적 여과","파국화","흑백 논리");
        assertThat(values("SELECT definition FROM distortion_tags WHERE name='파국화'"))
                .containsExactly("작은 일이나 불확실한 일을 감당할 수 없는 최악의 결과로 부풀려 생각하는 것을 말해요.");
        assertThat(values("""
                SELECT name FROM distortion_tags WHERE length(trim(example_thoughts->>0))>0
                    AND length(trim(self_questions->>0))>0 AND length(trim(reframes->>0))>0
                ORDER BY display_order,id
                """)).hasSize(11).doesNotContain("파국화");
    }

    @Test void commonCardsAreOrderedAndTheReservedCatalogStaysEmpty() throws Exception {
        assertThat(values("""
                SELECT id || ':' || title FROM positive_solutions WHERE situation_type_id IS NULL
                    AND active AND category_code='resilience' AND length(trim(solution_content))>0 ORDER BY display_order,id
                """)).containsExactly("7:다시 일어서는 힘","8:조용한 명상","9:충분한 휴식과 숙면");
        assertThat(values("SELECT id::text FROM anxiety_patterns")).isEmpty();
    }

    private List<String> values(String sql) throws Exception {
        var values = new ArrayList<String>();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            while (result.next()) values.add(result.getString(1));
        }
        return values;
    }
}
