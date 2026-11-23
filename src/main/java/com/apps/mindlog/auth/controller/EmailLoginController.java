package com.apps.mindlog.auth.controller;

import com.apps.mindlog.auth.dto.EmailLoginRequest;
import com.apps.mindlog.auth.dto.EmailLoginResponse;
import com.apps.mindlog.auth.service.EmailLoginService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/auth", version = "1")
public class EmailLoginController {
    private final EmailLoginService logins;
    public EmailLoginController(EmailLoginService logins) { this.logins = logins; }

    @PostMapping("/email-sessions")
    public ResponseEntity<EmailLoginResponse> login(@Valid @RequestBody EmailLoginRequest request) {
        return ResponseEntity.ok(logins.login(request));
    }
}
