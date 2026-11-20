package com.jia.academy.auth.api;

import com.jia.academy.auth.api.AuthDtos.*;
import com.jia.academy.auth.service.AuthService;
import com.jia.academy.auth.service.AuthRateLimitService;
import com.jia.academy.auth.service.PasswordResetService;
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
    private final PasswordResetService passwordResets;

    public AuthController(AuthService auth, AuthRateLimitService rateLimits, PasswordResetService passwordResets) {
        this.auth = auth;
        this.rateLimits = rateLimits;
        this.passwordResets = passwordResets;
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

    @PostMapping("/password/reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public java.util.Map<String, String> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResets.requestReset(request.email());
        return java.util.Map.of("message", "If an active account exists, password reset instructions will be sent.");
    }

    @PostMapping("/password/reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResets.confirmReset(request.token(), request.newPassword());
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
