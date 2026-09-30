package com.apps.mindlog.after.entity;
import jakarta.persistence.*;
@Entity @Table(name="after_analysis_cards")
public class AfterAnalysisCard extends AfterChildEntity {
    @Column(nullable=false) private long inputVersion;
    private Long distortionTagId;
    @Column(nullable=false) private String tagLabel;
    @Column(nullable=false) private String title;
    @Column(nullable=false) private String content;
    @Column(nullable=false) private int displayOrder;
    protected AfterAnalysisCard(){}
    public AfterAnalysisCard(long afterId,long version,Long tagId,String tagLabel,String title,String content,int order){
        super(afterId);inputVersion=version;distortionTagId=tagId;this.tagLabel=tagLabel;
        this.title=title;this.content=content;displayOrder=order;
    }
    public long getInputVersion(){return inputVersion;}public Long getDistortionTagId(){return distortionTagId;}
    public String getTagLabel(){return tagLabel;}public String getTitle(){return title;}
    public String getContent(){return content;}public int getDisplayOrder(){return displayOrder;}
}
