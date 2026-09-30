package com.apps.mindlog.after.dto.response;
import com.apps.mindlog.after.entity.AnalysisStatus;
import java.time.OffsetDateTime;
public record LatestAnalysisResponse(AnalysisStatus analysisStatus,long inputVersion,OffsetDateTime completedAt,AnalysisResult result){}
