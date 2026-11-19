package com.jia.academy.content.api;

import com.jia.academy.content.api.ContentAuthoringDtos.*;
import com.jia.academy.content.service.ContentAuthoringService;
import com.jia.academy.content.service.LessonImportService;
import com.jia.academy.content.api.LessonImportDtos.BatchRequest;
import com.jia.academy.content.api.LessonImportDtos.BatchResponse;
import com.jia.academy.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class ContentAuthoringController {
    private final ContentAuthoringService content;
    private final LessonImportService imports;

    public ContentAuthoringController(ContentAuthoringService content, LessonImportService imports) {
        this.content = content;
        this.imports = imports;
    }

    @GetMapping("/lessons")
    @PreAuthorize("hasAuthority('content.create')")
    public PageResult<LessonAdminListItem> lessons(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return content.adminLessons(status, q, page, size);
    }

    @GetMapping("/lessons/{lessonId}")
    @PreAuthorize("hasAuthority('content.create')")
    public LessonAuthoringDetail lesson(@PathVariable UUID lessonId) {
        return content.authoringDetail(lessonId);
    }

    @PostMapping("/lessons/import")
    @PreAuthorize("hasAuthority('content.create')")
    public BatchResponse importLessons(@AuthenticationPrincipal CurrentUser actor,
                                       @Valid @RequestBody BatchRequest request) {
        return imports.importLessons(actor, request);
    }

    @PostMapping("/lessons")
    @PreAuthorize("hasAuthority('content.create')")
    public LessonDraftResponse createDraft(@AuthenticationPrincipal CurrentUser actor,
                                           @Valid @RequestBody LessonDraftRequest request) {
        return content.createDraft(actor, request);
    }

    @PutMapping("/lessons/{lessonId}")
    @PreAuthorize("hasAuthority('content.create')")
    public LessonDraftResponse updateDraft(@AuthenticationPrincipal CurrentUser actor,
                                           @PathVariable UUID lessonId,
                                           @Valid @RequestBody LessonDraftRequest request) {
        return content.updateDraft(actor, lessonId, request);
    }

    @PostMapping("/lessons/{lessonId}/submit")
    @PreAuthorize("hasAuthority('content.create')")
    public Map<String, Object> submit(@AuthenticationPrincipal CurrentUser actor,
                                      @PathVariable UUID lessonId) {
        return content.submitForReview(actor, lessonId);
    }

    @GetMapping("/reviews")
    @PreAuthorize("hasAuthority('content.review')")
    public PageResult<ReviewQueueItem> reviewQueue(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return content.reviewQueue(q, page, size);
    }

    @GetMapping("/reviews/{taskId}")
    @PreAuthorize("hasAuthority('content.review')")
    public ReviewDetail review(@PathVariable UUID taskId) {
        return content.reviewDetail(taskId);
    }

    @PostMapping("/reviews/{taskId}/decision")
    @PreAuthorize("hasAuthority('content.review')")
    public Map<String, Object> decide(@AuthenticationPrincipal CurrentUser actor,
                                      @PathVariable UUID taskId,
                                      @Valid @RequestBody ReviewDecisionRequest request) {
        return content.decideReview(actor, taskId, request);
    }

    @PostMapping("/lessons/{lessonId}/publish")
    @PreAuthorize("hasAuthority('content.publish')")
    public Map<String, Object> publish(@AuthenticationPrincipal CurrentUser actor,
                                       @PathVariable UUID lessonId) {
        return content.publish(actor, lessonId);
    }
}
