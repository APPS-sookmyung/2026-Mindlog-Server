package com.apps.mindlog.after.service;

import com.apps.mindlog.after.dto.request.FinalDiaryRequest;
import com.apps.mindlog.after.dto.response.FinalDiaryResponse;
import com.apps.mindlog.after.entity.*;
import com.apps.mindlog.after.repository.*;
import com.apps.mindlog.ai.job.*;
import com.apps.mindlog.global.config.TimeConfig;
import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import com.apps.mindlog.global.idempotency.*;
import com.apps.mindlog.notification.service.AfterNotificationResolver;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class FinalDiaryService {
    private final AfterAccess access;
    private final AfterLogRepository logs;
    private final AfterAiFeedbackRepository feedbacks;
    private final JobStore jobs;
    private final IdempotencyService idempotency;
    private final AfterNotificationResolver notifications;
    private final JsonMapper json;
    private final Clock clock;
    public FinalDiaryService(AfterAccess access,AfterLogRepository logs,AfterAiFeedbackRepository feedbacks,
            JobStore jobs,IdempotencyService idempotency,AfterNotificationResolver notifications,JsonMapper json,Clock clock){
        this.access=access;this.logs=logs;this.feedbacks=feedbacks;this.jobs=jobs;this.idempotency=idempotency;
        this.notifications=notifications;this.json=json;this.clock=clock;
    }
    @Transactional
    public IdempotencyRecord confirm(long id,String key,FinalDiaryRequest request){
        var account=access.lockCurrent();
        var existing=logs.findById(id).orElseThrow(AfterAccess::notFound);
        var before=access.lockBeforePath(existing.getBeforeLogId(),account.id());
        var after=logs.lockById(id).orElseThrow(AfterAccess::notFound);
        var scope=new IdempotencyService.Scope(account.id(),account.dataGeneration(),"after:"+id+":final-diary");
        return idempotency.execute(scope,key,json.writeValueAsString(request),
                // Account and record locks above remain held before replay as well.
                ()->access.beforePath(after.getBeforeLogId(),account.id()),()->{
            if(after.getRecordStatus()==RecordStatus.FINALIZED)throw new ApiException(ErrorType.FINAL_DIARY_ALREADY_CONFIRMED);
            after.requireVersion(request.getInputVersion());
            var feedback=feedbacks.findByAfterLogId(id).filter(value->value.getInputVersion()==after.getInputVersion()
                    &&value.getAnalysisStatus()==AnalysisStatus.COMPLETED).orElseThrow(FinalDiaryService::notReady);
            if(jobs.hasActive(id,JobKind.AFTER_ANALYSIS)||!jobs.hasCompleted(account.id(),account.dataGeneration(),id,JobKind.DIARY_DRAFT,after.getInputVersion()))
                throw notReady();
            after.confirm(request.getInputVersion(),request.getFinalDiary(),request.getActualScore(),after.getActualScoreComment(),clock.instant());
            logs.flush();notifications.resolve(account.id(),before.id());
            // Durable registration is atomic; dispatcher can only see it after commit.
            String snapshot=json.writeValueAsString(Map.of("before",before,"freeWriting",after.getFreeWriting(),
                    "finalDiary",after.getFinalDiary(),"actualScore",after.getActualScore(),"analysisSummary",feedback.getSummary()==null?"":feedback.getSummary()));
            jobs.submit(account.id(),account.dataGeneration(),null,id,JobKind.RECORD_INSIGHT,after.getInputVersion(),UUID.randomUUID(),snapshot);
            var response=new FinalDiaryResponse(id,after.getFinalDiary(),before.expectedScore(),after.getActualScore(),
                    after.getActualScore()-before.expectedScore(),RecordStatus.FINALIZED,after.getInputVersion(),
                    FinalDiaryResponse.Comparison.of(before.expectedScore(),after.getActualScore(),account.nickname(),before.expectedScoreComment(),after.getActualScoreComment()),
                    JobStatus.PENDING,after.getFinalizedAt().atZone(TimeConfig.SEOUL).toOffsetDateTime());
            return new IdempotencyRecord(200,json.writeValueAsString(response),Map.of("Content-Type","application/json"));
        });
    }
    private static ApiException notReady(){return new ApiException(ErrorType.ANALYSIS_NOT_READY,"현재 입력의 분석과 일기 초안 생성이 완료되어야 합니다.");}
}
