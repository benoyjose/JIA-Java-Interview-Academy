package com.jia.academy.privacy.api;

import com.jia.academy.privacy.service.LearnerPrivacyExportService;
import com.jia.academy.security.CurrentUser;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Returns a JSON portability archive for the signed-in learner only. */
@RestController
@RequestMapping("/api/v1/me/privacy")
@PreAuthorize("isAuthenticated()")
public class LearnerPrivacyController {
    private final LearnerPrivacyExportService exports;

    public LearnerPrivacyController(LearnerPrivacyExportService exports) {
        this.exports = exports;
    }

    @GetMapping("/export")
    public Map<String, Object> export(@AuthenticationPrincipal CurrentUser user) {
        return exports.export(user);
    }
}
