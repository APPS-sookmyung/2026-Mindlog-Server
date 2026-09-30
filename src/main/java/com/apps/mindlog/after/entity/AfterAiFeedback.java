package com.apps.mindlog.after.entity;

import com.apps.mindlog.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="af_ai_feedbacks")
public class AfterAiFeedback extends BaseTimeEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,updatable=false) private Long afterLogId;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private AnalysisStatus analysisStatus;
    @Column(nullable=false) private long inputVersion;
    private String summary;
    private String modelVersion;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="json") private String inputSnapshot;
    private Instant completedAt;
    protected AfterAiFeedback(){}
    public AfterAiFeedback(long afterId,long version){afterLogId=afterId;inputVersion=version;analysisStatus=AnalysisStatus.NOT_REQUESTED;}
    public void pending(long version){clear(version);analysisStatus=AnalysisStatus.PENDING;}
    public void processing(){analysisStatus=AnalysisStatus.PROCESSING;}
    public void failed(){summary=null;modelVersion=null;inputSnapshot=null;completedAt=null;analysisStatus=AnalysisStatus.FAILED;}
    public void invalidate(long version){clear(version);analysisStatus=AnalysisStatus.INVALIDATED;}
    public void complete(long version,String summary,String modelVersion,String inputSnapshot,Instant at){
        if(inputVersion!=version)throw new IllegalStateException("Stale analysis result");
        this.summary=summary;this.modelVersion=modelVersion;this.inputSnapshot=inputSnapshot;
        completedAt=at;analysisStatus=AnalysisStatus.COMPLETED;
    }
    private void clear(long version){inputVersion=version;summary=null;modelVersion=null;inputSnapshot=null;completedAt=null;}
    public Long getId(){return id;}public Long getAfterLogId(){return afterLogId;}
    public AnalysisStatus getAnalysisStatus(){return analysisStatus;}public long getInputVersion(){return inputVersion;}
    public String getSummary(){return summary;}public String getModelVersion(){return modelVersion;}
    public String getInputSnapshot(){return inputSnapshot;}public Instant getCompletedAt(){return completedAt;}
}
