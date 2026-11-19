package com.jia.academy.content.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.jia.academy.content.service.QuestionImportService;
import com.jia.academy.security.CurrentUser;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/questions")
public class QuestionImportController {
    private final QuestionImportService imports;

    public QuestionImportController(QuestionImportService imports) {
        this.imports = imports;
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('content.create')")
    public Map<String, Object> importQuestions(@AuthenticationPrincipal CurrentUser actor,
                                               @RequestBody JsonNode request) {
        return imports.importQuestions(actor, request);
    }
}
