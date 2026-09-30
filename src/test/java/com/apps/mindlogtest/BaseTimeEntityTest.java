package com.apps.mindlogtest;

import com.apps.mindlog.global.common.BaseTimeEntity;

import com.apps.mindlog.global.config.JpaConfig;
import com.apps.mindlog.global.config.TimeConfig;
import jakarta.persistence.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;

@org.springframework.test.context.ContextConfiguration(classes = com.apps.mindlog.MindlogApplication.class)
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.data.jpa.repositories.enabled=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, BaseTimeEntityTest.Config.class})

class BaseTimeEntityTest {

    @Autowired EntityManager em;
    @Autowired MutableClock clock;

    @Test void insertAndUpdateUseInjectedClockAndPreserveCreationTime() {
        Instant first = Instant.parse("2026-09-29T14:59:59Z");
        clock.current.set(first);
        var record = new AuditedRecord();
        record.description = "before";
        em.persist(record);
        em.flush();
        Long id = record.id;
        em.clear();
        record = em.find(AuditedRecord.class, id);
        assertThat(record.getCreatedAt()).isEqualTo(first);
        assertThat(record.getUpdatedAt()).isEqualTo(first);

        Instant second = first.plusSeconds(2);
        clock.current.set(second);
        record.description = "after";
        em.flush();
        em.clear();
        record = em.find(AuditedRecord.class, id);
        assertThat(record.getCreatedAt()).isEqualTo(first);
        assertThat(record.getUpdatedAt()).isEqualTo(second);
    }

    @Test void readOnlyFlushDoesNotChangeTimestamp() {
        Instant first = Instant.parse("2026-09-29T00:00:00Z");
        clock.current.set(first);
        var record = new AuditedRecord();
        em.persist(record);
        em.flush();
        clock.current.set(first.plusSeconds(60));
        em.flush();
        em.clear();
        assertThat(em.find(AuditedRecord.class, record.id).getUpdatedAt()).isEqualTo(first);
    }

    @Entity(name = "AuditedRecord")
    @Table(name = "auditing_test_records")
    static class AuditedRecord extends BaseTimeEntity {
        @Id @GeneratedValue Long id;
        String description;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = AuditedRecord.class)
    static class Config {
        @Bean MutableClock clock() { return new MutableClock(); }
        @Bean @ServiceConnection PostgreSQLContainer postgres() { return new PostgreSQLContainer("postgres:16"); }
    }

    static class MutableClock extends Clock {
        final AtomicReference<Instant> current = new AtomicReference<>();
        public ZoneId getZone() { return TimeConfig.SEOUL; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        public Instant instant() { return current.get(); }
    }
}
