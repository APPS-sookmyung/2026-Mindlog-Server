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
    public List<JobKind> supportedKinds() { return handlers.values().stream().filter(JobHandler::ready).map(JobHandler::kind).toList(); }

    @Job(name="AI job", retries=0)
    public void run(UUID id) {
        var found=store.find(id);
        if (found.isEmpty()) return;
        var handler=handlers.get(found.get().kind());
        if (handler==null || !handler.ready()) return;
        var claimed=store.claim(id);
        if (claimed.isEmpty()) return;
        var attempt=claimed.get();
        try {
            if (!store.mayCompute(attempt,handler,()->handler.started(attempt))) return;
            var result=handler.compute(attempt);
            store.complete(attempt,result,handler,()->handler.apply(attempt,result));
        } catch (com.apps.mindlog.global.error.exception.ApiException limited) {
            var reason=limited.getErrorType()==com.apps.mindlog.global.error.ErrorType.RATE_LIMITED?JobFailure.RATE_LIMITED:JobFailure.PROVIDER_UNAVAILABLE;
            store.fail(attempt,reason,handler,()->handler.failed(attempt,reason));
        } catch (IllegalArgumentException invalid) {
            store.fail(attempt,JobFailure.INVALID_OUTPUT,handler,()->handler.failed(attempt,JobFailure.INVALID_OUTPUT));
        } catch (Exception unavailable) {
            // Do not serialize/log provider exception details or private input.
            store.fail(attempt,JobFailure.PROVIDER_UNAVAILABLE,handler,()->handler.failed(attempt,JobFailure.PROVIDER_UNAVAILABLE));
        }
    }
}
