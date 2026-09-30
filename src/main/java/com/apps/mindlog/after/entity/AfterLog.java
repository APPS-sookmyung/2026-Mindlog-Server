package com.apps.mindlog.after.entity;

import com.apps.mindlog.global.common.BaseTimeEntity;
import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import com.apps.mindlog.global.validation.VisibleLength;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name="after_logs")
public class AfterLog extends BaseTimeEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,updatable=false) private Long beforeLogId;
    @NotNull @VisibleLength(min=1,max=300) @Column(nullable=false) private String freeWriting;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private RecordStatus recordStatus;
    @Column(nullable=false) private long inputVersion;
    @Min(0) @Max(100) private Integer actualScore;
    private String actualScoreComment;
    @VisibleLength(min=1,max=5000) private String finalDiary;
    private Instant finalizedAt;
    protected AfterLog(){}
    public static AfterLog draft(long beforeId,String freeWriting){
        if(beforeId<=0)throw new IllegalArgumentException("Before ID required");
        var value=new AfterLog();value.beforeLogId=beforeId;value.freeWriting=text(freeWriting);
        value.recordStatus=RecordStatus.DRAFT;value.inputVersion=1;return value;
    }
    public boolean replaceInput(long version,String writing,boolean symptomsChanged){
        requireDraft();requireVersion(version);String normalized=text(writing);
        if(!symptomsChanged&&Objects.equals(freeWriting,normalized))return false;
        freeWriting=normalized;inputVersion++;return true;
    }
    public void confirm(long version,String diary,int score,String comment,Instant now){
        if(recordStatus==RecordStatus.FINALIZED)throw new ApiException(ErrorType.FINAL_DIARY_ALREADY_CONFIRMED);
        requireVersion(version);
        if(score<0||score>100)throw new ApiException(ErrorType.VALIDATION_ERROR);
        finalDiary=text(diary);actualScore=score;actualScoreComment=comment;
        finalizedAt=Objects.requireNonNull(now);recordStatus=RecordStatus.FINALIZED;
    }
    public void requireDraft(){if(recordStatus!=RecordStatus.DRAFT)throw new ApiException(ErrorType.AFTER_LOG_FINALIZED);}
    public void requireVersion(long version){if(inputVersion!=version)throw new ApiException(ErrorType.STALE_INPUT_VERSION);}
    private static String text(String value){
        if(value==null||value.isBlank())throw new ApiException(ErrorType.VALIDATION_ERROR);
        return value.strip();
    }
    public Long getId(){return id;}public Long getBeforeLogId(){return beforeLogId;}
    public String getFreeWriting(){return freeWriting;}public RecordStatus getRecordStatus(){return recordStatus;}
    public long getInputVersion(){return inputVersion;}public Integer getActualScore(){return actualScore;}
    public String getActualScoreComment(){return actualScoreComment;}public String getFinalDiary(){return finalDiary;}
    public Instant getFinalizedAt(){return finalizedAt;}
}
