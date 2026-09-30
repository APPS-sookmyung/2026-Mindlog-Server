package com.apps.mindlog.after;

import com.apps.mindlog.after.entity.*;
import com.apps.mindlog.after.repository.*;
import com.apps.mindlog.global.config.*;
import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.*;
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

@DataJpaTest(properties="spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class,TimeConfig.class,AfterEntityTest.Database.class})
class AfterEntityTest {
    @Autowired AfterLogRepository logs;
    @Autowired AfterAiFeedbackRepository feedbacks;
    @Autowired AfterSymptomRepository symptoms;
    @Autowired AfterDistortionRepository distortions;
    @Autowired AfterAnalysisCardRepository cards;
    @Autowired AiInsightRepository insights;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    static final Instant NOW=Instant.parse("2026-09-30T00:00:00Z");
    @BeforeEach void fixture(){
        jdbc.update("""
            INSERT INTO users(id,email,nickname,representative_character_id,account_status,
                after_reminder_enabled,weekly_report_start_day,created_at,updated_at)
            VALUES(900,'entity@test.example','테스트',1,'active',true,'sun',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """);
        jdbc.update("""
            INSERT INTO before_logs(id,user_id,situation_type_id,scheduled_at,expected_score,worst_scenario,created_at,updated_at)
            VALUES(900,900,1,'2026-09-30',80,'원문',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """);
        jdbc.update("""
            INSERT INTO distortion_tags(id,name,definition,example_thoughts,self_questions,reframes,display_order,active,created_at,updated_at)
            VALUES(900,'테스트','테스트','[]','[]','[]',1,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """);
    }
    AfterLog draft(){return logs.saveAndFlush(AfterLog.draft(900," 원문 "));}
    @Test void draftHasNoFinalValuesOrSituationAndHasAuditTimes(){
        var value=draft();em.clear();value=logs.findByBeforeLogId(900L).orElseThrow();
        assertThat(value.getRecordStatus()).isEqualTo(RecordStatus.DRAFT);
        assertThat(value.getFreeWriting()).isEqualTo("원문");assertThat(value.getInputVersion()).isEqualTo(1);
        assertThat(value.getActualScore()).isNull();assertThat(value.getFinalDiary()).isNull();
        assertThat(value.getFinalizedAt()).isNull();assertThat(value.getCreatedAt()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_name='after_logs' AND column_name='situation_type_id'",Integer.class)).isZero();
    }
    @Test void onlySemanticInputChangeIncrementsVersionAndFinalizationPreservesIt(){
        var value=draft();assertThat(value.replaceInput(1,"원문",false)).isFalse();
        assertThat(value.replaceInput(1,"원문",true)).isTrue();assertThat(value.getInputVersion()).isEqualTo(2);
        value.confirm(2," 최종 일기 ",52,"생각보다 잘 됐어요",NOW);logs.flush();em.clear();
        var saved=logs.findById(value.getId()).orElseThrow();
        assertThat(saved.getRecordStatus()).isEqualTo(RecordStatus.FINALIZED);
        assertThat(saved.getFinalDiary()).isEqualTo("최종 일기");assertThat(saved.getActualScore()).isEqualTo(52);
        assertThat(saved.getActualScoreComment()).isEqualTo("생각보다 잘 됐어요");
        assertThat(saved.getFinalizedAt()).isEqualTo(NOW);assertThat(saved.getInputVersion()).isEqualTo(2);
    }
    @Test void staleAndFinalizedMutationsAreRejected(){
        var value=draft();assertError(()->value.replaceInput(2,"수정",false),ErrorType.STALE_INPUT_VERSION);
        assertError(()->value.confirm(2,"일기",30,null,NOW),ErrorType.STALE_INPUT_VERSION);
        value.confirm(1,"일기",30,null,NOW);
        assertError(()->value.replaceInput(1,"수정",false),ErrorType.AFTER_LOG_FINALIZED);
        assertError(()->value.confirm(1,"다시",30,null,NOW),ErrorType.FINAL_DIARY_ALREADY_CONFIRMED);
    }
    @Test void beforeCanHaveOnlyOneAfter(){
        draft();assertThatThrownBy(()->logs.saveAndFlush(AfterLog.draft(900,"다른 원문")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void originalVisibleLengthIsValidatedOnPersistence(){
        assertThatThrownBy(()->logs.saveAndFlush(AfterLog.draft(900,"가".repeat(301))))
                .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
    }
    @Test void latestFeedbackCanBeInvalidatedWithoutKeepingOldContent(){
        var after=draft();var feedback=feedbacks.save(new AfterAiFeedback(after.getId(),1));
        feedback.pending(1);feedback.processing();feedback.complete(1,"요약","test-model","{\"text\":\"원문\"}",NOW);
        feedbacks.flush();em.clear();var saved=feedbacks.findByAfterLogId(after.getId()).orElseThrow();
        assertThat(saved.getInputSnapshot()).contains("원문");assertThat(saved.getAnalysisStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        saved.invalidate(2);feedbacks.flush();em.clear();saved=feedbacks.findByAfterLogId(after.getId()).orElseThrow();
        assertThat(saved.getAnalysisStatus()).isEqualTo(AnalysisStatus.INVALIDATED);
        assertThat(saved.getInputVersion()).isEqualTo(2);assertThat(saved.getSummary()).isNull();
        assertThat(saved.getCompletedAt()).isNull();assertThat(saved.getInputSnapshot()).isNull();
    }
    @Test void symptomsEvidenceCardsAndInsightsRoundTrip(){
        var after=draft();long id=after.getId();symptoms.save(new AfterSymptom(id,1));
        distortions.save(new AfterDistortion(id,900,"원문","다른 해석"));
        cards.save(new AfterAnalysisCard(id,1,900L,"테스트","제목","설명",2));
        cards.save(new AfterAnalysisCard(id,1,null,"돌아보기","일반 카드","설명",1));
        insights.save(new AiInsight(id,InsightType.REFLECTION_QUESTION,1,"다음에는?"));em.flush();em.clear();
        assertThat(symptoms.findByAfterLogIdOrderByBodySymptomIdAsc(id)).extracting(AfterSymptom::getBodySymptomId).containsExactly(1L);
        assertThat(distortions.findByAfterLogIdOrderByIdAsc(id).getFirst().getEvidence()).isEqualTo("원문");
        assertThat(cards.findByAfterLogIdOrderByDisplayOrderAscIdAsc(id)).extracting(AfterAnalysisCard::getTitle).containsExactly("일반 카드","제목");
        assertThat(insights.findByAfterLogIdOrderByDisplayOrderAscIdAsc(id).getFirst().getInsightType()).isEqualTo(InsightType.REFLECTION_QUESTION);
        assertThat(jdbc.queryForObject("SELECT insight_type FROM ai_insights WHERE after_log_id=?",String.class,id)).isEqualTo("reflection_question");
    }
    @Test void reservedInsightCodesAreNotRecordDetailTypes(){
        assertThat(InsightType.EXTERNAL_FACTOR.recordDetail()).isFalse();
        assertThat(InsightType.FACTOR_FEEDBACK.recordDetail()).isFalse();
        assertThat(InsightType.RECORD_COMPARISON.recordDetail()).isTrue();
    }
    void assertError(Runnable action,ErrorType type){assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getErrorType()).isEqualTo(type));}
    @TestConfiguration(proxyBeanMethods=false)
    public static class Database {
        @Bean @ServiceConnection PostgreSQLContainer postgres(){return new PostgreSQLContainer("postgres:16");}
    }
}
