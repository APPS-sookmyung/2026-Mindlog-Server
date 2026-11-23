package com.apps.mindlog.before.controller;

import com.apps.mindlog.before.dto.request.CreateBeforeRequest;
import com.apps.mindlog.before.service.BeforeLogService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/before-logs", version = "1")
public class BeforeLogController {
    private final BeforeLogService service;
    public BeforeLogController(BeforeLogService service) { this.service = service; }
    @PostMapping
    public ResponseEntity<String> create(@Valid @RequestBody CreateBeforeRequest request, HttpServletRequest servletRequest) {
        return service.create(request, servletRequest);
    }
}
