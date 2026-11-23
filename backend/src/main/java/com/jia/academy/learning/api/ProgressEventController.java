package com.jia.academy.learning.api;

import com.jia.academy.learning.service.ProgressEventService;
import com.jia.academy.learning.service.ProgressEventService.EventReceipt;
import com.jia.academy.learning.service.ProgressEventService.BatchReceipt;
import com.jia.academy.learning.service.ProgressEventService.EventRequest;
import com.jia.academy.learning.service.ProgressEventService.ProgressHistory;
import com.jia.academy.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/me/progress-events")
@PreAuthorize("isAuthenticated()")
public class ProgressEventController {
    private final ProgressEventService events;
    public ProgressEventController(ProgressEventService events) { this.events = events; }

    @PostMapping("/batch")
    public BatchReceipt appendBatch(@AuthenticationPrincipal CurrentUser user,
                                    @RequestBody BatchEventRequest request) {
        return events.appendBatch(user, request == null ? null : request.events());
    }

    public record BatchEventRequest(List<EventRequest> events) {}

    @PostMapping
    public EventReceipt append(@AuthenticationPrincipal CurrentUser user,
                               @Valid @RequestBody EventRequest request) {
        return events.append(user, request);
    }
    @GetMapping("/history")
    public ProgressHistory history(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(defaultValue = "30") int size, @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String eventType) {
        return events.history(user, size, cursor, eventType);
    }
}
