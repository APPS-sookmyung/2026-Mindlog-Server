package com.apps.mindlog.after.controller;
import com.apps.mindlog.after.dto.request.InputVersionRequest;
import com.apps.mindlog.after.service.DiaryDraftService;
import com.apps.mindlog.ai.job.JobResponse;
import com.apps.mindlog.global.idempotency.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping(path="/api/after-logs/{afterLogId}/diary-drafts",version="1")
@SecurityRequirement(name="bearerAuth")
public class DiaryDraftController {
    private final DiaryDraftService service;
    public DiaryDraftController(DiaryDraftService service){this.service=service;}
    @PostMapping public ResponseEntity<String> request(@PathVariable long afterLogId,@Valid @RequestBody InputVersionRequest body,HttpServletRequest request){
        return IdempotencyResponses.response(service.request(afterLogId,IdempotencyKeyFilter.requireKey(request),body));
    }
    @GetMapping("/{jobId}") public JobResponse.DraftPoll poll(@PathVariable long afterLogId,@PathVariable UUID jobId){return service.poll(afterLogId,jobId);}
}
