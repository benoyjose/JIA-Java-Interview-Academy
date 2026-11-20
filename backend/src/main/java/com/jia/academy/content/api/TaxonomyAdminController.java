package com.jia.academy.content.api;

import com.jia.academy.content.service.TaxonomyAdminService;
import com.jia.academy.content.service.TaxonomyAdminService.CategoryItem;
import com.jia.academy.content.service.TaxonomyAdminService.CategoryRequest;
import com.jia.academy.content.service.TaxonomyAdminService.TechnologyRequest;
import com.jia.academy.content.service.TaxonomyAdminService.TechnologyItem;
import com.jia.academy.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/taxonomy")
@PreAuthorize("hasAuthority('content.create')")
public class TaxonomyAdminController {
    private final TaxonomyAdminService taxonomy;

    public TaxonomyAdminController(TaxonomyAdminService taxonomy) {
        this.taxonomy = taxonomy;
    }

    @GetMapping("/technologies")
    public List<TechnologyItem> technologies() {
        return taxonomy.technologies();
    }

    @PostMapping("/technologies")
    @ResponseStatus(HttpStatus.CREATED)
    public TechnologyItem createTechnology(@AuthenticationPrincipal CurrentUser actor,
                                           @Valid @RequestBody TechnologyRequest request) {
        return taxonomy.createTechnology(actor, request);
    }

    @PutMapping("/technologies/{technologyId}")
    public TechnologyItem updateTechnology(@AuthenticationPrincipal CurrentUser actor,
            @PathVariable UUID technologyId, @Valid @RequestBody TechnologyRequest request) {
        return taxonomy.updateTechnology(actor, technologyId, request);
    }

    @DeleteMapping("/technologies/{technologyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveTechnology(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID technologyId) {
        taxonomy.archiveTechnology(actor, technologyId);
    }

    @GetMapping("/categories")
    public List<CategoryItem> categories(@RequestParam(required = false) String technology) {
        return taxonomy.categories(technology);
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryItem create(@AuthenticationPrincipal CurrentUser actor,
                               @Valid @RequestBody CategoryRequest request) {
        return taxonomy.create(actor, request);
    }

    @PutMapping("/categories/{categoryId}")
    public CategoryItem update(@AuthenticationPrincipal CurrentUser actor,
                               @PathVariable UUID categoryId,
                               @Valid @RequestBody CategoryRequest request) {
        return taxonomy.update(actor, categoryId, request);
    }

    @DeleteMapping("/categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID categoryId) {
        taxonomy.archive(actor, categoryId);
    }
}
