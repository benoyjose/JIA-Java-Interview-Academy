package com.jia.academy.content.api;

import com.jia.academy.content.service.AdminOperationsService;
import com.jia.academy.content.service.AdminOperationsService.PageResult;
import com.jia.academy.content.service.AdminOperationsService.QuestionItem;
import com.jia.academy.content.service.AdminOperationsService.UserItem;
import com.jia.academy.content.service.AdminOperationsService.UserUpdate;
import com.jia.academy.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminOperationsController {
    private final AdminOperationsService operations;

    public AdminOperationsController(AdminOperationsService operations) {
        this.operations = operations;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('users.manage')")
    public PageResult<UserItem> users(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return operations.users(q, page, size);
    }

    @PutMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('users.manage')")
    public UserItem updateUser(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID userId,
                               @Valid @RequestBody UserUpdate request) {
        return operations.updateUser(actor, userId, request);
    }

    @GetMapping("/questions")
    @PreAuthorize("hasAuthority('content.create')")
    public PageResult<QuestionItem> questions(@RequestParam(required = false) String q,
            @RequestParam(required = false) String status, @RequestParam(required = false) String difficulty,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return operations.questions(q, status, difficulty, page, size);
    }

    @PatchMapping("/questions/{questionId}/status")
    @PreAuthorize("hasAuthority('content.create')")
    public QuestionItem transitionQuestion(@AuthenticationPrincipal CurrentUser actor,
            @PathVariable UUID questionId, @RequestBody QuestionStatusRequest request) {
        return operations.transitionQuestion(actor, questionId, request.status());
    }

    public record QuestionStatusRequest(String status) {}
}
