package com.apps.mindlog.after.dto.response;
import com.apps.mindlog.after.entity.*;
import com.apps.mindlog.reference.dto.ReferenceResponses.Choice;
import java.time.*;
import java.util.List;
public record AfterDetailResponse(long id,long beforeLogId,long situationTypeId,String situationTypeName,
        String title,String description,RecordStatus recordStatus,long inputVersion,LocalDate scheduledAt,
        String worstScenario,String freeWriting,List<Choice> bodySymptoms,int expectedScore,Integer actualScore,
        Integer scoreDiff,String finalDiary,OffsetDateTime finalizedAt,AnalysisStatus analysisStatus,
        OffsetDateTime createdAt,OffsetDateTime updatedAt){
    public AfterDetailResponse{bodySymptoms=List.copyOf(bodySymptoms);}
}
