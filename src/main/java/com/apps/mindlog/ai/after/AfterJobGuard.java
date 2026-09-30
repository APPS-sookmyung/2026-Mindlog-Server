package com.apps.mindlog.ai.after;

import com.apps.mindlog.after.entity.RecordStatus;
import com.apps.mindlog.after.repository.AfterLogRepository;
import com.apps.mindlog.after.service.AfterAccess;
import com.apps.mindlog.ai.job.AsyncJob;
import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import org.springframework.stereotype.Component;

@Component
public class AfterJobGuard {
    private final AfterAccess access;
    private final AfterLogRepository logs;
    public AfterJobGuard(AfterAccess access,AfterLogRepository logs){this.access=access;this.logs=logs;}
    public boolean available(){return access.available();}
    public boolean lockCurrent(AsyncJob job,RecordStatus required){
        if(job.afterLogId()==null)return false;
        var account=access.accounts().lock(job.userId());
        if(account.isEmpty()||account.get().dataGeneration()!=job.dataGeneration()||!account.get().onboardingCompleted())return false;
        var found=logs.findById(job.afterLogId());if(found.isEmpty())return false;
        try{access.lockBeforePath(found.get().getBeforeLogId(),job.userId());}
        catch(ApiException missing){if(missing.getErrorType()==ErrorType.RESOURCE_NOT_FOUND)return false;throw missing;}
        var after=logs.lockById(job.afterLogId());
        return after.isPresent()&&after.get().getInputVersion()==job.inputVersion()&&after.get().getRecordStatus()==required;
    }
}
