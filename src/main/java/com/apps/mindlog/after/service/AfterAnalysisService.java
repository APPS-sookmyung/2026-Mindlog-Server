package com.apps.mindlog.after.service;

import com.apps.mindlog.after.dto.request.InputVersionRequest;
import com.apps.mindlog.after.entity.AfterAiFeedback;
import com.apps.mindlog.after.repository.*;
import com.apps.mindlog.ai.common.LlmQuotaGuard;
import com.apps.mindlog.ai.job.*;
import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import com.apps.mindlog.global.idempotency.*;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AfterAnalysisService {
    private final AfterAccess access;
    private final AfterLogRepository logs;
    private final AfterSymptomRepository symptoms;
    private final AfterAiFeedbackRepository feedbacks;
    private final IdempotencyService idempotency;
    private final LlmQuotaGuard quota;
    private final JobStore jobs;
    private final JobQueryService query;
    private final JsonMapper json;
    public AfterAnalysisService(AfterAccess access,AfterLogRepository logs,AfterSymptomRepository symptoms,
            AfterAiFeedbackRepository feedbacks,IdempotencyService idempotency,LlmQuotaGuard quota,
            JobStore jobs,JobQueryService query,JsonMapper json){
        this.access=access;this.logs=logs;this.symptoms=symptoms;this.feedbacks=feedbacks;
        this.idempotency=idempotency;this.quota=quota;this.jobs=jobs;this.query=query;this.json=json;
    }
    @Transactional
    public IdempotencyRecord request(long id,String key,InputVersionRequest request){
        var account=access.lockCurrent();
        var existing=logs.findById(id).orElseThrow(AfterAccess::notFound);
        var before=access.lockBeforePath(existing.getBeforeLogId(),account.id());
        var after=logs.lockById(id).orElseThrow(AfterAccess::notFound);
        var scope=new IdempotencyService.Scope(account.id(),account.dataGeneration(),"after:"+id+":analysis");
        return idempotency.execute(scope,key,json.writeValueAsString(request),()->access.beforePath(before.id(),account.id()),()->{
            after.requireDraft();after.requireVersion(request.getInputVersion());
            if(jobs.hasActive(id,JobKind.AFTER_ANALYSIS))throw new ApiException(ErrorType.ANALYSIS_IN_PROGRESS);
            quota.consume(account.id());
            var feedback=feedbacks.findByAfterLogId(id).orElseGet(()->new AfterAiFeedback(id,after.getInputVersion()));
            feedback.pending(after.getInputVersion());feedbacks.saveAndFlush(feedback);
            var snapshot=Map.of("before",before,"freeWriting",after.getFreeWriting(),"bodySymptomIds",
                    symptoms.findByAfterLogIdOrderByBodySymptomIdAsc(id).stream().map(value->value.getBodySymptomId()).toList());
            UUID jobId=jobs.submit(account.id(),account.dataGeneration(),null,id,JobKind.AFTER_ANALYSIS,after.getInputVersion(),
                    UUID.randomUUID(),json.writeValueAsString(snapshot));
            var response=new JobResponse.AfterAccepted(jobId,id,JobStatus.PENDING,after.getInputVersion());
            return new IdempotencyRecord(202,json.writeValueAsString(response),Map.of("Content-Type","application/json","Retry-After","2"));
        });
    }
    @Transactional(readOnly=true)
    public JobResponse.Poll poll(long afterId,UUID jobId){
        var account=access.current();
        var after=logs.findById(afterId).orElseThrow(AfterAccess::notFound);
        access.beforePath(after.getBeforeLogId(),account.id());
        return query.poll(new JobQueryService.Scope(account.id(),account.dataGeneration(),JobKind.AFTER_ANALYSIS,afterId),jobId);
    }
}
