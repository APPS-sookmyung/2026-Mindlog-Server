package com.apps.mindlog.after.entity;
import jakarta.persistence.*;
@Entity @Table(name="after_distortions")
public class AfterDistortion extends AfterChildEntity {
    @Column(nullable=false) private Long distortionTagId;
    @Column(nullable=false) private String evidence;
    @Column(nullable=false) private String explanation;
    protected AfterDistortion(){}
    public AfterDistortion(long afterId,long tagId,String evidence,String explanation){
        super(afterId);distortionTagId=tagId;this.evidence=evidence;this.explanation=explanation;
    }
    public Long getDistortionTagId(){return distortionTagId;}public String getEvidence(){return evidence;}
    public String getExplanation(){return explanation;}
}
