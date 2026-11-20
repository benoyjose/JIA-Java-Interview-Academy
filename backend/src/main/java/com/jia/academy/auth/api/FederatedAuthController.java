package com.jia.academy.auth.api;

import com.jia.academy.auth.api.AuthDtos.TokenResponse;
import com.jia.academy.auth.service.FederatedAuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.jia.academy.security.CurrentUser;

@RestController
@RequestMapping("/api/v1/auth/federated")
public class FederatedAuthController {
    private final FederatedAuthService federated;

    public FederatedAuthController(FederatedAuthService federated) {
        this.federated = federated;
    }

    @PostMapping("/google")
    public TokenResponse google(@Valid @RequestBody ProviderTokenRequest request) {
        return federated.google(request.idToken(), request.nonce(), request.deviceName());
    }

    @PostMapping("/apple")
    public TokenResponse apple(@Valid @RequestBody AppleTokenRequest request) {
        return federated.apple(request.identityToken(), request.nonce(), request.deviceName());
    }

    @PostMapping("/link/google")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void linkGoogle(@AuthenticationPrincipal CurrentUser actor, @Valid @RequestBody ProviderTokenRequest request) {
        federated.linkGoogle(actor, request.idToken(), request.nonce());
    }

    @PostMapping("/link/apple")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void linkApple(@AuthenticationPrincipal CurrentUser actor, @Valid @RequestBody AppleTokenRequest request) {
        federated.linkApple(actor, request.identityToken(), request.nonce());
    }

    @DeleteMapping("/link/{provider}")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void unlinkIdentity(@AuthenticationPrincipal CurrentUser actor, @PathVariable String provider) {
        federated.unlinkIdentity(actor, provider);
    }

    public record ProviderTokenRequest(@NotBlank @Size(max=12000) String idToken,
                                       @NotBlank @Size(max=256) String nonce,
                                       @Size(max=100) String deviceName) {}
    public record AppleTokenRequest(@NotBlank @Size(max=12000) String identityToken,
                                    @NotBlank @Size(max=256) String nonce,
                                    @Size(max=100) String deviceName) {}
}
