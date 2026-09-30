package com.apps.mindlog.after.entity;
import jakarta.persistence.*;
@Converter(autoApply=true)
public class InsightTypeConverter implements AttributeConverter<InsightType,String>{
    public String convertToDatabaseColumn(InsightType type){return type==null?null:type.code();}
    public InsightType convertToEntityAttribute(String value){return value==null?null:InsightType.fromCode(value);}
}
