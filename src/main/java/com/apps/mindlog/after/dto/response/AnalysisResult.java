package com.apps.mindlog.after.dto.response;

import java.util.List;

public record AnalysisResult(String summary,int patternCount,List<Distortion> distortions,List<Card> analysisCards){
    public AnalysisResult{distortions=List.copyOf(distortions);analysisCards=List.copyOf(analysisCards);}
    public record Distortion(long distortionTagId,String distortionTagName,String evidence,String explanation){}
    public record Card(String tagLabel,Long distortionTagId,String title,String content){}
}
