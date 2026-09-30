package com.apps.mindlog.global.config;

import java.time.ZoneId;
import java.util.Objects;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.context.annotation.Configuration;

/** Domain services register their daily jobs at startup using a stable ID and a service method reference. */
@Configuration(proxyBeanMethods = false)
public class SchedulerConfig {
    public static final String DAILY_CRON = "0 4 * * *";
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private final JobScheduler scheduler;

    public SchedulerConfig(JobScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public String scheduleDaily(String id, JobLambda job) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Daily job ID is required");
        return scheduler.scheduleRecurrently(id, DAILY_CRON, ZONE, Objects.requireNonNull(job));
    }
}
