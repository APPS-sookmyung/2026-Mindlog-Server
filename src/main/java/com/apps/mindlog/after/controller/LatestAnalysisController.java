package com.apps.mindlog.after.controller;
import com.apps.mindlog.after.dto.response.LatestAnalysisResponse;
import com.apps.mindlog.after.service.AfterAnalysisQueryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping(path="/api/after-logs/{afterLogId}/analysis",version="1")
@SecurityRequirement(name="bearerAuth")
public class LatestAnalysisController {
    private final AfterAnalysisQueryService service;
    public LatestAnalysisController(AfterAnalysisQueryService service){this.service=service;}
    @GetMapping public LatestAnalysisResponse latest(@PathVariable long afterLogId){return service.latest(afterLogId);}
}
