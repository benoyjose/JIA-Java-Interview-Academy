package com.jia.academy.learning.api;

import com.jia.academy.learning.service.LearnerProgressSnapshotService;
import com.jia.academy.learning.service.LearnerProgressSnapshotService.Snapshot;
import com.jia.academy.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/progress-snapshot")
@PreAuthorize("isAuthenticated()")
public class LearnerProgressSnapshotController {
    private final LearnerProgressSnapshotService service;
    public LearnerProgressSnapshotController(LearnerProgressSnapshotService service) { this.service=service; }

    @GetMapping
    public Snapshot get(@AuthenticationPrincipal CurrentUser user,
                        @RequestParam(defaultValue="50") int size,
                        @RequestParam(required=false) String afterSlug) {
        return service.load(user,size,afterSlug);
    }
}
