package com.apps.mindlog.after.dto.response;

import com.apps.mindlog.after.entity.*;
import java.time.OffsetDateTime;
import java.util.List;

public record AfterCreatedResponse(Long id,Long beforeLogId,long situationTypeId,String situationTypeName,
        String freeWriting,List<Symptom> bodySymptoms,Integer actualScore,RecordStatus recordStatus,
        long inputVersion,OffsetDateTime finalizedAt,AnalysisStatus analysisStatus,OffsetDateTime createdAt){
    public AfterCreatedResponse{bodySymptoms=List.copyOf(bodySymptoms);}
    public record Symptom(long id,String name){}
}
