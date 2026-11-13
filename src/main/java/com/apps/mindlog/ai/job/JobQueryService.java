package com.apps.mindlog.ai.job;

import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class JobQueryService {
    private final JobStore store;
    private final Clock clock;
    private final JsonMapper json;
    public JobQueryService(JobStore store,Clock clock,JsonMapper json) {
        this.store=store;this.clock=clock;this.json=json;
    }
    /** Scope is constructed by the service from authenticated account and owned path, never request body. */
    public record Scope(long userId,long dataGeneration,JobKind kind,Long afterLogId) {
        public Scope {
            if(userId<=0||dataGeneration<0||kind==null
                    ||(kind==JobKind.BEFORE_REBUTTAL?afterLogId!=null:afterLogId==null||afterLogId<=0))
                throw new IllegalArgumentException("Invalid polling scope");
        }
    }
    public JobResponse.Poll poll(Scope scope,UUID id) {
        var job=store.find(id).filter(value->value.userId()==scope.userId()
                &&value.dataGeneration()==scope.dataGeneration()&&value.kind()==scope.kind()
                &&Objects.equals(value.afterLogId(),scope.afterLogId())&&!value.expired(clock.instant()))
                .orElseThrow(()->new ApiException(ErrorType.RESOURCE_NOT_FOUND,"요청한 리소스를 찾을 수 없습니다."));
        return JobResponse.poll(job,job.status()==JobStatus.COMPLETED&&job.result()!=null?json.readTree(job.result()):null);
    }
    public JobResponse.DraftPoll draft(Scope scope,UUID id) {
        if(scope.kind()!=JobKind.DIARY_DRAFT)throw new IllegalArgumentException("Diary draft scope required");
        var value=poll(scope,id);
        return new JobResponse.DraftPoll(value.jobId(),value.status(),value.inputVersion(),value.result(),value.failure());
    }
}
