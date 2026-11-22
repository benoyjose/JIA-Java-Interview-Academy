package com.jia.academy.appregistry.api;

import com.jia.academy.appregistry.service.AcademyAppRegistryService;
import com.jia.academy.appregistry.service.AcademyAppRegistryService.ReleasePolicy;
import com.jia.academy.appregistry.service.AcademyAppRegistryService.ReleaseUpdate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/apps")
public class AcademyAppRegistryController {
    private final AcademyAppRegistryService registry;

    public AcademyAppRegistryController(AcademyAppRegistryService registry) {
        this.registry = registry;
    }

    @GetMapping("/{appCode}/release-policy")
    public ReleasePolicy releasePolicy(@PathVariable String appCode,
            @RequestParam String platform, @RequestParam String applicationId,
            @RequestParam(required = false) String installedVersion) {
        return registry.policy(appCode, platform, applicationId, installedVersion);
    }

    @PutMapping("/{appCode}/release-policy")
    @PreAuthorize("hasAuthority('releases.manage')")
    public ReleasePolicy updateReleasePolicy(@PathVariable String appCode,
            @Valid @RequestBody ReleaseUpdateRequest request) {
        return registry.update(appCode, new ReleaseUpdate(request.status(), request.minimumSupportedVersion(),
                request.currentVersion(), request.storeUrl(), request.releaseNotes()));
    }

    public record ReleaseUpdateRequest(
            @Size(max = 20) String status,
            @Size(max = 40) String minimumSupportedVersion,
            @Size(max = 40) String currentVersion,
            @Size(max = 1000) String storeUrl,
            @Size(max = 5000) String releaseNotes) {}
}
