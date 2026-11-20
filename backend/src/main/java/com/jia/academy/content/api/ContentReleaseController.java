package com.jia.academy.content.api;

import com.jia.academy.content.service.ContentReleaseService;
import com.jia.academy.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ContentReleaseController {
    private final ContentReleaseService releases;

    public ContentReleaseController(ContentReleaseService releases) {
        this.releases = releases;
    }

    @GetMapping("/content/releases/latest")
    public ContentReleaseResponse latest(@AuthenticationPrincipal CurrentUser user) {
        return releases.latest(user);
    }

    @GetMapping("/content/releases/{version}")
    public ContentReleaseResponse get(@PathVariable long version, @AuthenticationPrincipal CurrentUser user) {
        return releases.get(version, user);
    }

    @GetMapping("/content/releases/{version}/lessons/{slug}")
    public com.jia.academy.catalogue.api.CatalogueDtos.LessonDetailResponse lesson(
            @PathVariable long version, @PathVariable String slug, @AuthenticationPrincipal CurrentUser user) {
        return releases.lesson(version, slug, user);
    }

    @PostMapping("/admin/content-releases")
    @PreAuthorize("hasAuthority('releases.manage')")
    public ContentReleaseResponse create(@AuthenticationPrincipal CurrentUser actor) {
        return releases.create(actor);
    }
}
