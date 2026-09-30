package com.apps.mindlog.after.entity;
import jakarta.persistence.*;
@Entity @Table(name="after_symptoms")
public class AfterSymptom extends AfterChildEntity {
    @Column(nullable=false,updatable=false) private Long bodySymptomId;
    protected AfterSymptom(){}
    public AfterSymptom(long afterId,long symptomId){super(afterId);bodySymptomId=symptomId;}
    public Long getBodySymptomId(){return bodySymptomId;}
}
