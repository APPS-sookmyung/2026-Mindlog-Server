package com.apps.mindlog.after.service;

import com.apps.mindlog.after.dto.request.InputVersionRequest;
import com.apps.mindlog.after.entity.AnalysisStatus;
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
public class DiaryDraftService {
    private final AfterAccess access;
    private final AfterLogRepository logs;
    private final AfterAiFeedbackRepository feedbacks;
    private final AfterAnalysisQueryService analyses;
    private final IdempotencyService idempotency;
    private final LlmQuotaGuard quota;
    private final JobStore jobs;
    private final JobQueryService query;
    private final JsonMapper json;
    public DiaryDraftService(AfterAccess access,AfterLogRepository logs,AfterAiFeedbackRepository feedbacks,
            AfterAnalysisQueryService analyses,IdempotencyService idempotency,LlmQuotaGuard quota,
            JobStore jobs,JobQueryService query,JsonMapper json){
        this.access=access;this.logs=logs;this.feedbacks=feedbacks;this.analyses=analyses;
        this.idempotency=idempotency;this.quota=quota;this.jobs=jobs;this.query=query;this.json=json;
    }
    @Transactional
    public IdempotencyRecord request(long id,String key,InputVersionRequest request){
        var account=access.lockCurrent();var existing=logs.findById(id).orElseThrow(AfterAccess::notFound);
        var before=access.lockBeforePath(existing.getBeforeLogId(),account.id());
        var after=logs.lockById(id).orElseThrow(AfterAccess::notFound);
        var scope=new IdempotencyService.Scope(account.id(),account.dataGeneration(),"after:"+id+":diary-draft");
        return idempotency.execute(scope,key,json.writeValueAsString(request),()->access.beforePath(before.id(),account.id()),()->{
            after.requireDraft();after.requireVersion(request.getInputVersion());
            feedbacks.findByAfterLogId(id).filter(value->value.getInputVersion()==after.getInputVersion()
                    &&value.getAnalysisStatus()==AnalysisStatus.COMPLETED).orElseThrow(()->new ApiException(ErrorType.ANALYSIS_NOT_READY));
            if(jobs.hasActive(id,JobKind.AFTER_ANALYSIS))throw new ApiException(ErrorType.ANALYSIS_NOT_READY);
            if(jobs.hasActive(id,JobKind.DIARY_DRAFT))throw new ApiException(ErrorType.ANALYSIS_IN_PROGRESS);
            quota.consume(account.id());
            var snapshot=Map.of("before",before,"freeWriting",after.getFreeWriting(),"analysis",analyses.latest(id).result());
            UUID jobId=jobs.submit(account.id(),account.dataGeneration(),null,id,JobKind.DIARY_DRAFT,after.getInputVersion(),UUID.randomUUID(),json.writeValueAsString(snapshot));
            return new IdempotencyRecord(202,json.writeValueAsString(new JobResponse.AfterAccepted(jobId,id,JobStatus.PENDING,after.getInputVersion())),
                    Map.of("Content-Type","application/json","Retry-After","2"));
        });
    }
    @Transactional(readOnly=true)
    public JobResponse.DraftPoll poll(long id,UUID jobId){
        var account=access.current();var after=logs.findById(id).orElseThrow(AfterAccess::notFound);
        access.beforePath(after.getBeforeLogId(),account.id());
        return query.draft(new JobQueryService.Scope(account.id(),account.dataGeneration(),JobKind.DIARY_DRAFT,id),jobId);
    }
}
