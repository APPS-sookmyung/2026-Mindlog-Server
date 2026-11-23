package com.apps.mindlog.auth.controller;

import com.apps.mindlog.auth.dto.SignupRequest;
import com.apps.mindlog.auth.service.EmailSignupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/users", version = "1")
public class SignupController {
    private final EmailSignupService signups;

    public SignupController(EmailSignupService signups) { this.signups = signups; }

    @PostMapping
    public ResponseEntity<String> signup(@Valid @RequestBody SignupRequest request, HttpServletRequest servletRequest) {
        String responseBody = signups.signup(request, servletRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.LOCATION, "/api/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .body(responseBody);
    }
}
