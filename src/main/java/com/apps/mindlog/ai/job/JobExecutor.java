package com.apps.mindlog.ai.job;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.stereotype.Component;

@Component
public class JobExecutor {
    private final JobStore store;
    private final Map<JobKind,JobHandler> handlers=new EnumMap<>(JobKind.class);
    public JobExecutor(JobStore store, List<JobHandler> handlers) {
        this.store=store;
        for (var handler:handlers) if (this.handlers.put(handler.kind(),handler)!=null)
            throw new IllegalStateException("Duplicate AI job handler");
    }
    public List<JobKind> supportedKinds() { return List.copyOf(handlers.keySet()); }

    @Job(name="AI job", retries=0)
    public void run(UUID id) {
        var found=store.find(id);
        if (found.isEmpty()) return;
        var handler=handlers.get(found.get().kind());
        if (handler==null) return;
        var claimed=store.claim(id);
        if (claimed.isEmpty()) return;
        var attempt=claimed.get();
        try {
            if (!store.mayCompute(attempt,handler)) return;
            var result=handler.compute(attempt);
            store.complete(attempt,result,handler,()->handler.apply(attempt,result));
        } catch (IllegalArgumentException invalid) {
            store.fail(attempt,JobFailure.INVALID_OUTPUT);
        } catch (Exception unavailable) {
            // Do not serialize/log provider exception details or private input.
            store.fail(attempt,JobFailure.PROVIDER_UNAVAILABLE);
        }
    }
}
