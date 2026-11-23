package com.apps.mindlog.auth.controller;

import com.apps.mindlog.auth.dto.RefreshSessionRequest;
import com.apps.mindlog.auth.dto.RefreshSessionResponse;
import com.apps.mindlog.auth.service.SessionLifecycleService;
import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import com.apps.mindlog.global.security.AccessTokenCodec;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/auth/sessions", version = "1")
public class SessionLifecycleController {
    private final SessionLifecycleService sessions;
    private final AccessTokenCodec tokens;
    private final java.time.Clock clock;

    public SessionLifecycleController(SessionLifecycleService sessions, AccessTokenCodec tokens, java.time.Clock clock) {
        this.sessions = sessions; this.tokens = tokens; this.clock = clock;
    }

    @PostMapping("/refresh")
    public RefreshSessionResponse refresh(@Valid @RequestBody RefreshSessionRequest request) {
        return sessions.refresh(request.refreshToken());
    }

    @DeleteMapping("/current")
    public ResponseEntity<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        AccessTokenCodec.VerifiedToken verified = tokens.verify(authorization.substring(7), clock);
        if (verified.sessionId() <= 0)
            throw new ApiException(ErrorType.INVALID_TOKEN, "현재 세션을 식별할 수 없습니다.");
        sessions.logout(verified.userId(), verified.sessionId());
        return ResponseEntity.noContent().build();
    }
}
