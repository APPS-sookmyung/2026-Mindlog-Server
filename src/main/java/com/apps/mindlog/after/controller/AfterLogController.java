package com.apps.mindlog.after.controller;

import com.apps.mindlog.after.dto.request.CreateAfterRequest;
import com.apps.mindlog.after.dto.response.AfterCreatedResponse;
import com.apps.mindlog.after.service.AfterLogService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping(path="/api/after-logs",version="1")
@SecurityRequirement(name="bearerAuth")
public class AfterLogController {
    private final AfterLogService service;
    public AfterLogController(AfterLogService service){this.service=service;}
    @PostMapping
    public ResponseEntity<AfterCreatedResponse> create(@Valid @RequestBody CreateAfterRequest request){
        var response=service.create(request);
        return ResponseEntity.created(URI.create("/api/after-logs/"+response.id())).body(response);
    }
}
