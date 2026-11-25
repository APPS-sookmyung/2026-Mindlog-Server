package com.apps.mindlog.home;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apps.mindlog.global.security.AccessTokenCodec;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Import(HomeApiTest.Database.class)
@TestPropertySource(properties = "mindlog.security.master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class HomeApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccessTokenCodec tokens;

    @BeforeEach
    void fixture() {
        jdbc.update("DELETE FROM users WHERE id=8801");
        jdbc.update("""
                INSERT INTO emotion_characters(id,code,name,display_order,active,created_at,updated_at)
                VALUES(1,'anger','?遺얘텚',1,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                ON CONFLICT(id) DO UPDATE SET active=true
                """);
        jdbc.update("""
                INSERT INTO situation_types(id,name,onboarding_selectable,before_selectable,display_order,active,created_at,updated_at)
                VALUES(1,'??쀫퓮',true,true,1,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                ON CONFLICT(id) DO UPDATE SET active=true
                """);
        jdbc.update("""
                INSERT INTO users(id,email,nickname,representative_character_id,account_status,onboarding_completed,
                    after_reminder_enabled,weekly_report_start_day,created_at,updated_at)
                VALUES(8801,'home-test@example.com','??딅??쎈뱜',1,'active',true,true,'mon',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
    }

    @Test
    void returnsReducedContractAndEmptySectionsWithoutRecords() throws Exception {
        mvc.perform(get("/api/home").headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.greeting.timeOfDay").isNotEmpty())
                .andExpect(jsonPath("$.greeting.nickname").value("??딅??쎈뱜"))
                .andExpect(jsonPath("$.representativeCharacter.code").value("anger"))
                .andExpect(jsonPath("$.recentRecords").isArray())
                .andExpect(jsonPath("$.recentRecords.length()").value(0))
                .andExpect(jsonPath("$.weeklySummary.weekStartDay").value("mon"))
                .andExpect(jsonPath("$.weeklySummary.dailyBars.length()").value(7))
                .andExpect(jsonPath("$.failedSections.length()").value(0))
                .andExpect(jsonPath("$.notificationCard").doesNotExist())
                .andExpect(jsonPath("$.hasUnread").doesNotExist());
    }

    @Test
    void recentRecordsAreNewestFirstLimitedToThreeAndOnlyFinalizedHasActualScore() throws Exception {
        for (int i=1;i<=4;i++) {
            jdbc.update("""
                    INSERT INTO before_logs(id,user_id,situation_type_id,title,scheduled_at,expected_score,worst_scenario,created_at,updated_at)
                    VALUES(?,8801,1,?,DATE '2026-11-20',?, 'worst',TIMESTAMPTZ '2026-11-2%s 00:00:00+09',TIMESTAMPTZ '2026-11-2%s 00:00:00+09')
                    """.formatted(i,i),100+i,"기록"+i,60+i);
        }
        jdbc.update("""
                INSERT INTO after_logs(id,before_log_id,actual_score,free_writing,record_status,input_version,finalized_at,final_diary,created_at,updated_at)
                VALUES(91,102,NULL,'draft','DRAFT',1,NULL,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
                      (92,103,45,'final','FINALIZED',1,TIMESTAMPTZ '2026-11-23 09:00:00+09','diary',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        mvc.perform(get("/api/home").headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentRecords.length()").value(3))
                .andExpect(jsonPath("$.recentRecords[0].beforeLogId").value(104))
                .andExpect(jsonPath("$.recentRecords[1].beforeLogId").value(103))
                .andExpect(jsonPath("$.recentRecords[1].actualScore").value(45))
                .andExpect(jsonPath("$.recentRecords[2].beforeLogId").value(102))
                .andExpect(jsonPath("$.recentRecords[2].recordStatus").value("DRAFT"))
                .andExpect(jsonPath("$.recentRecords[2].actualScore").doesNotExist());
    }

    @Test
    void weeklyScoresBelongToFinalizationDateAndRespectSeoulWeekBoundary() throws Exception {
        jdbc.update("""
                INSERT INTO before_logs(id,user_id,situation_type_id,title,scheduled_at,expected_score,worst_scenario,created_at,updated_at)
                VALUES(101,8801,1,'past before',DATE '2026-11-10',90,'worst',TIMESTAMPTZ '2026-11-10 10:00:00+09',CURRENT_TIMESTAMP),
                      (102,8801,1,'current before',DATE '2026-11-10',20,'worst',TIMESTAMPTZ '2026-11-10 10:00:00+09',CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO after_logs(id,before_log_id,actual_score,free_writing,record_status,input_version,finalized_at,final_diary,created_at,updated_at)
                VALUES(101,101,80,'done','FINALIZED',1,TIMESTAMPTZ '2026-11-22 23:30:00+09','diary',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
                      (102,102,40,'done','FINALIZED',1,TIMESTAMPTZ '2026-11-23 00:30:00+09','diary',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        mvc.perform(get("/api/home").headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeklySummary.periodStart").value("2026-11-23"))
                .andExpect(jsonPath("$.weeklySummary.averageActualScore").value(40.0))
                .andExpect(jsonPath("$.weeklySummary.dailyBars[0].date").value("2026-11-23"))
                .andExpect(jsonPath("$.weeklySummary.dailyBars[0].averageActualScore").value(40.0))
                .andExpect(jsonPath("$.weeklySummary.dailyBars[0].recordCount").value(1));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/home").header("X-API-Version","1"))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.http.HttpHeaders authHeaders() {
        var headers=new org.springframework.http.HttpHeaders();
        headers.set("X-API-Version","1");
        headers.setBearerAuth(tokens.issue(8801,Instant.now()));
        return headers;
    }

    @TestConfiguration(proxyBeanMethods=false)
    static class Database {
        @Bean @ServiceConnection PostgreSQLContainer postgres(){return new PostgreSQLContainer("postgres:16-alpine");}
    }
}

