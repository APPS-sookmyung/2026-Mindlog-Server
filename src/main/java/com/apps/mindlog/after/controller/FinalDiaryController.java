package com.apps.mindlog.after.controller;

import com.apps.mindlog.after.dto.request.FinalDiaryRequest;
import com.apps.mindlog.after.service.FinalDiaryService;
import com.apps.mindlog.global.idempotency.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping(path="/api/after-logs/{afterLogId}/final-diary",version="1")
@SecurityRequirement(name="bearerAuth")
public class FinalDiaryController {
    private final FinalDiaryService service;
    public FinalDiaryController(FinalDiaryService service){this.service=service;}
    @PutMapping public ResponseEntity<String> confirm(@PathVariable long afterLogId,@Valid @RequestBody FinalDiaryRequest body,HttpServletRequest request){
        return IdempotencyResponses.response(service.confirm(afterLogId,IdempotencyKeyFilter.requireKey(request),body));
    }
}
