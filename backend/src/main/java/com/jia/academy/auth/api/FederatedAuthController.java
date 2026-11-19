package com.jia.academy.auth.api;

import com.jia.academy.auth.api.AuthDtos.TokenResponse;
import com.jia.academy.auth.service.FederatedAuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

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

    public record ProviderTokenRequest(@NotBlank @Size(max=12000) String idToken,
                                       @NotBlank @Size(max=256) String nonce,
                                       @Size(max=100) String deviceName) {}
    public record AppleTokenRequest(@NotBlank @Size(max=12000) String identityToken,
                                    @NotBlank @Size(max=256) String nonce,
                                    @Size(max=100) String deviceName) {}
}
