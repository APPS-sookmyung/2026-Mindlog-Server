package com.apps.mindlog.after.entity;
import java.util.Locale;
public enum InsightType {
    RECORD_COMPARISON,SITUATION_PATTERN,REFLECTION_QUESTION,EXTERNAL_FACTOR,FACTOR_FEEDBACK,COPING_SUGGESTION;
    public String code(){return name().toLowerCase(Locale.ROOT);}
    public boolean recordDetail(){return this==RECORD_COMPARISON||this==SITUATION_PATTERN||this==REFLECTION_QUESTION;}
    public static InsightType fromCode(String value){return valueOf(value.toUpperCase(Locale.ROOT));}
}
