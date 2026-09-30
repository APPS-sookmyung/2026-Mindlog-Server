package com.apps.mindlog.global.error.exception;

import com.apps.mindlog.global.error.ErrorType;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.OptionalLong;

/** All details and extensions must be safe for the requesting user. */
public class ApiException extends RuntimeException {
    private final ErrorType errorType;
    private final Long retryAfterSeconds;
    private final Map<String,Object> properties;
    public ApiException(ErrorType type){this(type,type.getTitle());}
    public ApiException(ErrorType type,String detail){this(type,detail,null,Map.of());}
    public ApiException(ErrorType type,String detail,long seconds){this(type,detail,Long.valueOf(seconds),Map.of());}
    public ApiException(ErrorType type,String detail,Map<String,Object> properties){this(type,detail,null,properties);}
    private ApiException(ErrorType type,String detail,Long seconds,Map<String,Object> properties){
        super(Objects.requireNonNull(detail));errorType=Objects.requireNonNull(type);retryAfterSeconds=seconds;
        if(type==ErrorType.LOGIN_RATE_LIMITED&&seconds==null)throw new IllegalArgumentException("Login rate limit requires Retry-After seconds");
        if(seconds!=null&&(seconds<0||type.getStatus().value()!=429))throw new IllegalArgumentException("Retry-After requires a 429 error and nonnegative seconds");
        if(properties.keySet().stream().anyMatch(Set.of("type","title","status","detail","instance")::contains))
            throw new IllegalArgumentException("Reserved problem field");
        this.properties=Map.copyOf(properties);
    }
    public ErrorType getErrorType(){return errorType;}
    public OptionalLong getRetryAfterSeconds(){return retryAfterSeconds==null?OptionalLong.empty():OptionalLong.of(retryAfterSeconds);}
    public Map<String,Object> getProperties(){return properties;}
}
