package com.apps.mindlog.after.service;

import com.apps.mindlog.after.dto.response.*;
import com.apps.mindlog.after.entity.*;
import com.apps.mindlog.after.repository.*;
import com.apps.mindlog.global.config.TimeConfig;
import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import com.apps.mindlog.global.security.OnboardingRequiredInterceptor;
import com.apps.mindlog.reference.service.ReferenceQueryService;
import java.time.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
public class AfterLogQueryService {
    private final AfterAccess access;
    private final AfterLogRepository logs;
    private final AfterListRepository list;
    private final AfterSymptomRepository symptoms;
    private final AfterAiFeedbackRepository feedbacks;
    private final ReferenceQueryService reference;
    public AfterLogQueryService(AfterAccess access,AfterLogRepository logs,AfterListRepository list,
            AfterSymptomRepository symptoms,AfterAiFeedbackRepository feedbacks,ReferenceQueryService reference){
        this.access=access;this.logs=logs;this.list=list;this.symptoms=symptoms;this.feedbacks=feedbacks;this.reference=reference;
    }
    public Page<AfterListResponse> list(LocalDate from,LocalDate to,Long situation,int page,int size){
        var account=access.current();OnboardingRequiredInterceptor.requireCompleted(account);
        if(page<0||size<1||(from!=null&&to!=null&&from.isAfter(to)))throw invalid();
        if(situation!=null)reference.requireExistingSituation(situation);
        try{
            return list.find(account.id(),from==null?null:from.atStartOfDay(TimeConfig.SEOUL).toInstant(),
                    to==null?null:to.plusDays(1).atStartOfDay(TimeConfig.SEOUL).toInstant(),situation,
                    PageRequest.of(page,Math.min(size,100)));
        }catch(DateTimeException badDate){throw invalid();}
    }
    public AfterDetailResponse detail(long id){
        var account=access.current();OnboardingRequiredInterceptor.requireCompleted(account);
        var after=logs.findById(id).orElseThrow(AfterAccess::notFound);
        var before=access.beforePath(after.getBeforeLogId(),account.id());
        var selected=reference.symptomsByIds(symptoms.findByAfterLogIdOrderByBodySymptomIdAsc(id).stream().map(AfterSymptom::getBodySymptomId).toList());
        Integer actual=after.getRecordStatus()==RecordStatus.FINALIZED?after.getActualScore():null;
        return new AfterDetailResponse(id,before.id(),before.situationTypeId(),before.situationTypeName(),before.title(),before.description(),
                after.getRecordStatus(),after.getInputVersion(),before.scheduledAt(),before.worstScenario(),after.getFreeWriting(),selected,
                before.expectedScore(),actual,actual==null?null:actual-before.expectedScore(),after.getFinalDiary(),time(after.getFinalizedAt()),
                analysisStatus(after),time(after.getCreatedAt()),time(after.getUpdatedAt()));
    }
    public AnalysisStatus analysisStatus(AfterLog after){
        return feedbacks.findByAfterLogId(after.getId()).map(value->value.getInputVersion()==after.getInputVersion()
                ?value.getAnalysisStatus():AnalysisStatus.INVALIDATED).orElse(AnalysisStatus.NOT_REQUESTED);
    }
    private static OffsetDateTime time(Instant value){return value==null?null:value.atZone(TimeConfig.SEOUL).toOffsetDateTime();}
    private static ApiException invalid(){return new ApiException(ErrorType.VALIDATION_ERROR,"조회 조건을 확인해 주세요.");}
}
