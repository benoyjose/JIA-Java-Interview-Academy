package com.jia.academy.learning.api;

import com.jia.academy.learning.service.LearningService;
import com.jia.academy.learning.service.LearningService.BookmarkItem;
import com.jia.academy.learning.service.LearningService.ProgressItem;
import com.jia.academy.learning.service.LearningService.ProgressRequest;
import com.jia.academy.learning.service.LearningService.SyncDiagnostics;
import com.jia.academy.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/me")
@PreAuthorize("isAuthenticated()")
public class LearningController {
    private final LearningService learning;

    public LearningController(LearningService learning) {
        this.learning = learning;
    }

    @GetMapping("/lesson-progress")
    public List<ProgressItem> progress(@AuthenticationPrincipal CurrentUser user) {
        return learning.progress(user);
    }

    @PutMapping("/lesson-progress/{lessonSlug}")
    public ProgressItem updateProgress(@AuthenticationPrincipal CurrentUser user,
            @PathVariable String lessonSlug, @Valid @RequestBody ProgressRequest request) {
        return learning.updateProgress(user, lessonSlug, request);
    }

    @GetMapping("/bookmarks")
    public List<BookmarkItem> bookmarks(@AuthenticationPrincipal CurrentUser user) {
        return learning.bookmarks(user);
    }

    @PutMapping("/bookmarks/{lessonSlug}")
    public java.util.Map<String, String> bookmark(@AuthenticationPrincipal CurrentUser user,
            @PathVariable String lessonSlug) {
        learning.bookmark(user, lessonSlug);
        return java.util.Map.of("status", "BOOKMARKED");
    }

    @DeleteMapping("/bookmarks/{lessonSlug}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeBookmark(@AuthenticationPrincipal CurrentUser user, @PathVariable String lessonSlug) {
        learning.removeBookmark(user, lessonSlug);
    }

    @GetMapping("/sync/diagnostics")
    public SyncDiagnostics diagnostics(@AuthenticationPrincipal CurrentUser user) {
        return learning.diagnostics(user);
    }
}
