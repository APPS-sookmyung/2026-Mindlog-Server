package com.apps.mindlog.after.service;

import com.apps.mindlog.after.dto.request.CreateAfterRequest;
import com.apps.mindlog.after.dto.response.AfterCreatedResponse;
import com.apps.mindlog.after.entity.*;
import com.apps.mindlog.after.repository.*;
import com.apps.mindlog.global.config.TimeConfig;
import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import com.apps.mindlog.reference.service.ReferenceValidator;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AfterLogService {
    private final AfterAccess access;
    private final AfterLogRepository logs;
    private final AfterSymptomRepository symptoms;
    private final ReferenceValidator reference;
    public AfterLogService(AfterAccess access,AfterLogRepository logs,AfterSymptomRepository symptoms,ReferenceValidator reference){
        this.access=access;this.logs=logs;this.symptoms=symptoms;this.reference=reference;
    }
    @Transactional
    public AfterCreatedResponse create(CreateAfterRequest request){
        var account=access.lockCurrent();
        var before=access.lockBeforeBody(request.beforeLogId(),account.id());
        logs.findByBeforeLogId(before.id()).ifPresent(existing->{
            throw new ApiException(ErrorType.AFTER_LOG_ALREADY_EXISTS,"이미 연결된 After 기록이 있습니다.",Map.of("afterLogId",existing.getId()));
        });
        var selected=reference.requireSymptoms(request.bodySymptomIds(),ReferenceValidator.SymptomContext.AFTER);
        var after=logs.saveAndFlush(AfterLog.draft(before.id(),request.freeWriting()));
        symptoms.saveAllAndFlush(selected.stream().map(value->new AfterSymptom(after.getId(),value.getId())).toList());
        return new AfterCreatedResponse(after.getId(),before.id(),before.situationTypeId(),before.situationTypeName(),
                after.getFreeWriting(),selected.stream().map(value->new AfterCreatedResponse.Symptom(value.getId(),value.getName())).toList(),
                null,RecordStatus.DRAFT,after.getInputVersion(),null,AnalysisStatus.NOT_REQUESTED,
                after.getCreatedAt().atZone(TimeConfig.SEOUL).toOffsetDateTime());
    }
}
