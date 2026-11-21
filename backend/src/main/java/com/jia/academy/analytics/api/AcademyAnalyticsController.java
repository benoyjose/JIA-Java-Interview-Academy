package com.jia.academy.analytics.api;

import com.jia.academy.analytics.service.AcademyAnalyticsService;
import com.jia.academy.analytics.service.AcademyAnalyticsService.AnalyticsSummary;
import com.jia.academy.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/analytics")
public class AcademyAnalyticsController {
    private final AcademyAnalyticsService analytics;

    public AcademyAnalyticsController(AcademyAnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('users.manage')")
    public AnalyticsSummary summary(@AuthenticationPrincipal CurrentUser actor,
            @RequestParam(defaultValue = "30") int windowDays) {
        return analytics.summary(actor, windowDays);
    }
}
