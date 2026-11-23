package com.apps.mindlog.after.dto.request;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
/** JSON integers only: do not silently truncate decimals or coerce numeric strings. */
final class AfterJsonInput {
    private AfterJsonInput(){}
    static long integer(JsonNode value){
        if(value==null||!value.isIntegralNumber()||!value.canConvertToLong())throw new IllegalArgumentException("Integer required");
        return value.longValue();
    }
    static List<Long> integers(JsonNode value){
        if(value==null||!value.isArray())throw new IllegalArgumentException("Integer array required");
        List<Long> result=new ArrayList<>();for(var item:value)result.add(integer(item));return List.copyOf(result);
    }
}
