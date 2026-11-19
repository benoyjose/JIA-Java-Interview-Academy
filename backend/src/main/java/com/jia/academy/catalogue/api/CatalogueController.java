package com.jia.academy.catalogue.api;
import com.jia.academy.catalogue.api.CatalogueDtos.*;
import com.jia.academy.catalogue.service.CatalogueService;
import java.util.List;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1")
public class CatalogueController {
 private final CatalogueService catalogue;
 public CatalogueController(CatalogueService catalogue){this.catalogue=catalogue;}
 @GetMapping("/technologies") public List<TechnologyResponse> technologies(){return catalogue.technologies();}
 @GetMapping("/technologies/{slug}") public TechnologyResponse technology(@PathVariable String slug){return catalogue.technology(slug);}
 @GetMapping("/technologies/{technologySlug}/categories") public List<CategoryResponse> categories(@PathVariable String technologySlug){return catalogue.categories(technologySlug);}
 @GetMapping("/lessons/{slug}") public LessonDetailResponse lesson(@PathVariable String slug){return catalogue.lesson(slug);}
 @GetMapping("/categories/{categorySlug}/lessons") public List<LessonSummaryResponse> lessons(@PathVariable String categorySlug){return catalogue.lessons(categorySlug);}
}
