package com.apps.mindlog.auth.controller;

import com.apps.mindlog.auth.service.EmailAvailabilityService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/auth", version = "1")
public class EmailAvailabilityController {
    private final EmailAvailabilityService service;

    public EmailAvailabilityController(EmailAvailabilityService service) {
        this.service = service;
    }

    @GetMapping("/email-availability")
    public EmailAvailabilityResponse check(@RequestParam(required = false) String email,
            HttpServletRequest request) {
        return service.check(email, request);
    }

    public record EmailAvailabilityResponse(String email, boolean available) { }
}
