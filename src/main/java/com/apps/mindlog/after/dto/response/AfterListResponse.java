package com.apps.mindlog.after.dto.response;
import com.apps.mindlog.after.entity.*;
import java.time.OffsetDateTime;
public record AfterListResponse(long id,long beforeLogId,long situationTypeId,String situationTypeName,
        int expectedScore,Integer actualScore,RecordStatus recordStatus,long inputVersion,
        AnalysisStatus analysisStatus,boolean hasFinalDiary,OffsetDateTime createdAt){}
