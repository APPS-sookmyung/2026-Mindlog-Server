package com.apps.mindlog.ai.job;

import org.jobrunr.scheduling.JobScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(name="mindlog.ai.dispatch-enabled",havingValue="true",matchIfMissing=true)
public class JobDispatcher {
    private final JobStore store;
    private final JobExecutor executor;
    private final JobScheduler scheduler;
    public JobDispatcher(JobStore store, JobExecutor executor, JobScheduler scheduler) {
        this.store=store;this.executor=executor;this.scheduler=scheduler;
    }
    @Scheduled(fixedDelay=30000,initialDelay=30000)
    public void dispatch() {
        for (var kind:executor.supportedKinds()) for (var id:store.dispatchable(kind)) {
            try { scheduler.enqueue(()->executor.run(id)); }
            catch (RuntimeException unavailable) { /* ai_jobs remains durable and eligible after 30 seconds. */ }
        }
        store.purgeExpiredBodies();
    }
    @Configuration(proxyBeanMethods=false)
    @EnableScheduling
    static class Scheduling {}
}
