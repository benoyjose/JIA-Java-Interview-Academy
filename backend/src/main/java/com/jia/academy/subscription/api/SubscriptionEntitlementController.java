package com.jia.academy.subscription.api;

import com.jia.academy.subscription.service.SubscriptionEntitlementService;
import com.jia.academy.subscription.service.SubscriptionEntitlementService.Entitlement;
import com.jia.academy.subscription.service.SubscriptionEntitlementService.SubscriptionSummary;
import com.jia.academy.subscription.service.SubscriptionEntitlementService.PlanSummary;
import com.jia.academy.subscription.service.SubscriptionEntitlementService.WebhookResult;
import com.jia.academy.security.CurrentUser;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class SubscriptionEntitlementController {
    private final SubscriptionEntitlementService service;

    public SubscriptionEntitlementController(SubscriptionEntitlementService service) {
        this.service = service;
    }

    @GetMapping("/me/academies/{academyCode}/entitlements")
    public List<Entitlement> entitlements(@AuthenticationPrincipal CurrentUser user,
            @PathVariable String academyCode) {
        return service.entitlements(user, academyCode);
    }

    @GetMapping("/me/academies/{academyCode}/subscriptions")
    public List<SubscriptionSummary> subscriptions(@AuthenticationPrincipal CurrentUser user,
            @PathVariable String academyCode) {
        return service.subscriptions(user, academyCode);
    }

    @GetMapping("/academies/{academyCode}/plans")
    public List<PlanSummary> plans(@PathVariable String academyCode) {
        return service.plans(academyCode);
    }

    @PostMapping("/payments/webhooks/{providerCode}")
    @ResponseStatus(HttpStatus.OK)
    public WebhookResult webhook(@PathVariable String providerCode,
            @RequestHeader(value = "X-Payment-Signature", required = false) String signature,
            @RequestBody String rawBody) {
        return service.acceptWebhook(providerCode, signature, rawBody);
    }
}
