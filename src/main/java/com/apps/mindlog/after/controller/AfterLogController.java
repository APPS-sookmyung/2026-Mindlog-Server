package com.apps.mindlog.after.controller;

import com.apps.mindlog.after.dto.request.CreateAfterRequest;
import com.apps.mindlog.after.dto.response.*;
import com.apps.mindlog.after.service.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping(path="/api/after-logs",version="1")
@SecurityRequirement(name="bearerAuth")
public class AfterLogController {
    private final AfterLogService service;
    private final AfterLogQueryService query;
    public AfterLogController(AfterLogService service,AfterLogQueryService query){this.service=service;this.query=query;}
    @PostMapping public ResponseEntity<AfterCreatedResponse> create(@Valid @RequestBody CreateAfterRequest request){
        var response=service.create(request);
        return ResponseEntity.created(URI.create("/api/after-logs/"+response.id())).body(response);
    }
    @GetMapping public Page<AfterListResponse> list(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required=false) Long situationTypeId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return query.list(from,to,situationTypeId,page,size);
    }
    @GetMapping("/{afterLogId}") public AfterDetailResponse detail(@PathVariable long afterLogId){return query.detail(afterLogId);}
}
