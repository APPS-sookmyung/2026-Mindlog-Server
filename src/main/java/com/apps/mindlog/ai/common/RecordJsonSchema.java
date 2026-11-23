package com.apps.mindlog.ai.common;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Provider-independent JSON schema for the record DTOs accepted by LlmClient. */
public final class RecordJsonSchema {
    private RecordJsonSchema() {}

    public static Map<String, Object> forRecord(Class<?> type) {
        if (!type.isRecord()) throw new IllegalArgumentException("LLM result must be a record");
        return schema(type, false, 0);
    }

    private static Map<String, Object> schema(Type type, boolean nullable, int depth) {
        if (depth > 8) throw new IllegalArgumentException("LLM schema is too deeply nested");
        var result = new LinkedHashMap<String, Object>();
        String kind;
        if (type instanceof ParameterizedType parameterized
                && parameterized.getRawType() == List.class) {
            kind = "array";
            result.put("items", schema(parameterized.getActualTypeArguments()[0], false, depth + 1));
        } else if (type instanceof Class<?> cls) {
            if (cls == String.class) kind = "string";
            else if (cls == int.class || cls == long.class || cls == Integer.class || cls == Long.class) kind = "integer";
            else if (cls == double.class || cls == Double.class || cls == java.math.BigDecimal.class) kind = "number";
            else if (cls == boolean.class || cls == Boolean.class) kind = "boolean";
            else if (cls.isEnum()) {
                kind = "string";
                var values = new ArrayList<Object>();
                for (Object value : cls.getEnumConstants()) values.add(((Enum<?>) value).name());
                if (nullable) values.add(null);
                result.put("enum", values);
            } else if (cls.isRecord()) {
                kind = "object";
                var properties = new LinkedHashMap<String, Object>();
                for (RecordComponent component : cls.getRecordComponents()) {
                    boolean optional = !component.getType().isPrimitive() && !required(component);
                    properties.put(component.getName(), schema(component.getGenericType(), optional, depth + 1));
                }
                result.put("properties", properties);
                result.put("required", List.copyOf(properties.keySet()));
                result.put("additionalProperties", false);
            } else throw new IllegalArgumentException("Unsupported LLM result field type");
        } else throw new IllegalArgumentException("Unsupported LLM result field type");
        result.put("type", nullable ? List.of(kind, "null") : kind);
        return result;
    }

    private static boolean required(RecordComponent component) {
        var type = component.getAnnotatedType();
        var accessor = component.getAccessor();
        return type.isAnnotationPresent(NotNull.class) || type.isAnnotationPresent(NotBlank.class)
                || type.isAnnotationPresent(NotEmpty.class) || accessor.isAnnotationPresent(NotNull.class)
                || accessor.isAnnotationPresent(NotBlank.class) || accessor.isAnnotationPresent(NotEmpty.class);
    }
}
