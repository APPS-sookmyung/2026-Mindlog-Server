package com.apps.mindlog.global.config;

import java.time.*;
import java.util.concurrent.*;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.storage.StorageProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.AsyncTaskExecutor;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "jobrunr.background-job-server.poll-interval-in-seconds=5")
@Import(JobRunrConfigTest.Config.class)
class JobRunrConfigTest {
    @Autowired JobScheduler jobs;
    @Autowired SchedulerConfig daily;
    @Autowired StorageProvider storage;
    @Autowired Probe probe;
    @Autowired AsyncTaskExecutor applicationTaskExecutor;

    @Test void persistsAndExecutesJobOnVirtualThread() {
        var id = jobs.enqueue(() -> probe.run());
        await().atMost(Duration.ofSeconds(40)).until(() -> probe.executed.isDone());
        assertThat(probe.executed.join()).isTrue();
        await().atMost(Duration.ofSeconds(20)).until(() -> storage.getJobById(id.asUUID()).hasState(org.jobrunr.jobs.states.StateName.SUCCEEDED));
    }

    @Test void failedJobIsPersistedAsFailed() {
        var id = jobs.enqueue(() -> probe.fail());
        await().atMost(Duration.ofSeconds(40)).until(() -> storage.getJobById(id.asUUID()).hasState(org.jobrunr.jobs.states.StateName.FAILED));
    }

    @Test void repeatingRegistrationUpdatesOneSeoulFourAmSchedule() {
        daily.scheduleDaily("test-daily", () -> probe.run());
        daily.scheduleDaily("test-daily", () -> probe.run());
        var matches = storage.getRecurringJobs().stream().filter(j -> j.getId().equals("test-daily")).toList();
        assertThat(matches).hasSize(1);
        var recurring = matches.getFirst();
        assertThat(recurring.getZoneId()).isEqualTo("Asia/Seoul");
        assertThat(recurring.getNextRun().atZone(SchedulerConfig.ZONE).toLocalTime()).isEqualTo(LocalTime.of(4, 0));
    }

    @Test void rejectsMissingStableId() {
        assertThatIllegalArgumentException().isThrownBy(() -> daily.scheduleDaily(" ", () -> probe.run()));
        assertThatIllegalArgumentException().isThrownBy(() -> daily.scheduleDaily(null, () -> probe.run()));
    }

    @Test void springAsyncExecutorAlsoUsesVirtualThreads() throws Exception {
        assertThat(applicationTaskExecutor.submit(() -> Thread.currentThread().isVirtual()).get(5, TimeUnit.SECONDS)).isTrue();
    }

    public static class Probe {
        final CompletableFuture<Boolean> executed = new CompletableFuture<>();
        public void run() { executed.complete(Thread.currentThread().isVirtual()); }
        @org.jobrunr.jobs.annotations.Job(retries = 0)
        public void fail() { throw new IllegalStateException("test job failure"); }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean @ServiceConnection PostgreSQLContainer postgres() { return new PostgreSQLContainer("postgres:16"); }
        @Bean Probe probe() { return new Probe(); }
    }
}
