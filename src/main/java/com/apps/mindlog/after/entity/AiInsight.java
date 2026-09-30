package com.apps.mindlog.after.entity;
import com.apps.mindlog.global.common.BaseTimeEntity;
import jakarta.persistence.*;
@Entity @Table(name="ai_insights")
public class AiInsight extends BaseTimeEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,updatable=false) private Long afterLogId;
    @Column(nullable=false) private InsightType insightType;
    @Column(nullable=false) private int displayOrder;
    @Column(nullable=false) private String insightContent;
    protected AiInsight(){}
    public AiInsight(long afterId,InsightType type,int order,String content){
        afterLogId=afterId;insightType=type;displayOrder=order;insightContent=content;
    }
    public Long getId(){return id;}public Long getAfterLogId(){return afterLogId;}
    public InsightType getInsightType(){return insightType;}public int getDisplayOrder(){return displayOrder;}
    public String getInsightContent(){return insightContent;}
}
