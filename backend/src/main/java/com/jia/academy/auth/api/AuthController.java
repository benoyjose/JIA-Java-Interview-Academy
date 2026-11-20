package com.jia.academy.auth.api;

import com.jia.academy.auth.api.AuthDtos.*;
import com.jia.academy.auth.service.AuthService;
import com.jia.academy.auth.service.AuthRateLimitService;
import com.jia.academy.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    private final AuthRateLimitService rateLimits;

    public AuthController(AuthService auth, AuthRateLimitService rateLimits) {
        this.auth = auth;
        this.rateLimits = rateLimits;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String remoteAddress = httpRequest.getRemoteAddr();
        rateLimits.checkAndRecord(remoteAddress, request.email());
        TokenResponse response = auth.login(request);
        rateLimits.clear(remoteAddress, request.email());
        return response;
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return auth.refresh(request.refreshToken());
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal CurrentUser user) {
        return auth.currentUser(user);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(name="Authorization") String authorization) {
        auth.logout(authorization.substring(7).trim());
    }
}
