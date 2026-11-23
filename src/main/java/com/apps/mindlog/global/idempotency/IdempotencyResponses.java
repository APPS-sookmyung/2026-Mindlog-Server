package com.apps.mindlog.global.idempotency;
import org.springframework.http.ResponseEntity;
public final class IdempotencyResponses {
    private IdempotencyResponses(){}
    public static ResponseEntity<String> response(IdempotencyRecord record){
        var builder=ResponseEntity.status(record.status());record.headers().forEach(builder::header);
        return builder.body(record.body());
    }
}
