package com.apps.mindlog.ai.job;

import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JobResponseTest {
    final Instant now=Instant.parse("2026-09-30T00:00:00Z");
    final UUID id=UUID.randomUUID();
    final JobStore store=mock(JobStore.class);
    final JsonMapper json=JsonMapper.builder().build();
    final JobQueryService service=new JobQueryService(store,Clock.fixed(now,ZoneOffset.UTC),json);
    JobQueryService.Scope scope(JobKind kind){return new JobQueryService.Scope(1,0,kind,kind==JobKind.BEFORE_REBUTTAL?null:2L);}
    AsyncJob job(JobKind kind,JobStatus status,Instant expires){
        return new AsyncJob(id,1,null,kind==JobKind.BEFORE_REBUTTAL?null:2L,kind,status,UUID.randomUUID(),3,0,
                "{\"secret\":\"private text\"}","{\"content\":\"일기\"}","PROVIDER_UNAVAILABLE",true,
                expires,now,now,UUID.randomUUID(),now.plusSeconds(60));
    }
    @Test void acceptedHas202AndRetryAfterTwo() {
        var body=new JobResponse.Accepted(id,JobStatus.PENDING,3);
        var response=JobResponse.accepted(body);
        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("2");
        assertThat(response.getBody()).isSameAs(body);
    }
    @Test void allStatesExposeOnlyMatchingPayloadAndFailure() {
        for(var status:JobStatus.values()){
            when(store.find(id)).thenReturn(Optional.of(job(JobKind.AFTER_ANALYSIS,status,null)));
            var poll=service.poll(scope(JobKind.AFTER_ANALYSIS),id);
            assertThat(poll.status()).isEqualTo(status);
            assertThat(poll.result()!=null).isEqualTo(status==JobStatus.COMPLETED);
            assertThat(poll.failure()!=null).isEqualTo(status==JobStatus.FAILED);
            assertThat(poll.inputVersion()).isEqualTo(3);
            assertThat(poll.completedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
        }
    }
    @Test void mismatchedUserGenerationKindOrTargetAndMissingAreIndistinguishable() {
        when(store.find(id)).thenReturn(Optional.of(job(JobKind.AFTER_ANALYSIS,JobStatus.COMPLETED,null)));
        for(var scope:List.of(new JobQueryService.Scope(2,0,JobKind.AFTER_ANALYSIS,2L),
                new JobQueryService.Scope(1,1,JobKind.AFTER_ANALYSIS,2L),scope(JobKind.DIARY_DRAFT),
                new JobQueryService.Scope(1,0,JobKind.AFTER_ANALYSIS,4L))) assertHidden(()->service.poll(scope,id));
        when(store.find(id)).thenReturn(Optional.empty());
        assertHidden(()->service.poll(scope(JobKind.AFTER_ANALYSIS),id));
    }
    @Test void expiryIsInclusiveEvenBeforeBackgroundCleanup() {
        when(store.find(id)).thenReturn(Optional.of(job(JobKind.DIARY_DRAFT,JobStatus.COMPLETED,now)));
        assertHidden(()->service.draft(scope(JobKind.DIARY_DRAFT),id));
        when(store.find(id)).thenReturn(Optional.of(job(JobKind.DIARY_DRAFT,JobStatus.COMPLETED,now.plusSeconds(1))));
        assertThat(service.draft(scope(JobKind.DIARY_DRAFT),id).draft().get("content").asText()).isEqualTo("일기");
    }
    @Test void serializedResponseNeverIncludesInternalInputOrIdentifiers() {
        when(store.find(id)).thenReturn(Optional.of(job(JobKind.DIARY_DRAFT,JobStatus.COMPLETED,null)));
        var tree=json.readTree(json.writeValueAsString(service.draft(scope(JobKind.DIARY_DRAFT),id)));
        assertThat(tree.has("draft")).isTrue();
        assertThat(tree.has("result")).isFalse();
        assertThat(tree.has("inputSnapshot")).isFalse();
        assertThat(tree.has("userId")).isFalse();
        assertThat(tree.has("requestKey")).isFalse();
        assertThat(tree.toString()).doesNotContain("private text","attemptToken","dataGeneration");
    }
    void assertHidden(Runnable action){
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,e->{
            assertThat(e.getErrorType()).isEqualTo(ErrorType.RESOURCE_NOT_FOUND);
            assertThat(e.getMessage()).isEqualTo("요청한 리소스를 찾을 수 없습니다.");
        });
    }
}
