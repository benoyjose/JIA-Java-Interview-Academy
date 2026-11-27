package com.jia.academy.content.api;

import com.jia.academy.content.service.CurriculumPrerequisiteAdminService;
import com.jia.academy.content.service.CurriculumPrerequisiteAdminService.PrerequisiteItem;
import com.jia.academy.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/lessons/{lessonId}/prerequisites")
@PreAuthorize("hasAuthority('content.create')")
public class CurriculumPrerequisiteAdminController {
    private final CurriculumPrerequisiteAdminService service;
    public CurriculumPrerequisiteAdminController(CurriculumPrerequisiteAdminService service) { this.service=service; }

    @GetMapping
    public List<PrerequisiteItem> get(@PathVariable UUID lessonId,
            @AuthenticationPrincipal CurrentUser actor) {
        return service.get(lessonId, actor);
    }

    @PutMapping
    public List<PrerequisiteItem> replace(@PathVariable UUID lessonId,
            @Valid @RequestBody ReplacePrerequisitesRequest request,
            @AuthenticationPrincipal CurrentUser actor) {
        return service.replace(lessonId, request.prerequisiteLessonIds(), actor);
    }
    public record ReplacePrerequisitesRequest(@NotNull List<@NotNull UUID> prerequisiteLessonIds) {}
}