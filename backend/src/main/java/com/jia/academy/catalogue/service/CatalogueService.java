package com.jia.academy.catalogue.service;
import com.jia.academy.catalogue.api.CatalogueDtos.*;
import com.jia.academy.catalogue.domain.PublicationStatus;
import com.jia.academy.catalogue.repository.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
@Service @Transactional(readOnly=true)
public class CatalogueService {
 private static final PublicationStatus VISIBLE=PublicationStatus.PUBLISHED;
 private final TechnologyRepository technologies; private final CategoryRepository categories; private final LessonRepository lessons;
 public CatalogueService(TechnologyRepository technologies,CategoryRepository categories,LessonRepository lessons){this.technologies=technologies;this.categories=categories;this.lessons=lessons;}
 public List<TechnologyResponse> technologies(){return technologies.findAllByStatusOrderBySortOrderAsc(VISIBLE).stream().map(t->new TechnologyResponse(t.getId(),t.getSlug(),t.getName(),t.getDescription(),t.getSortOrder())).toList();}
 public TechnologyResponse technology(String slug){var t=technologies.findBySlugAndStatus(slug,VISIBLE).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Technology not found"));return new TechnologyResponse(t.getId(),t.getSlug(),t.getName(),t.getDescription(),t.getSortOrder());}
 public List<CategoryResponse> categories(String technologySlug){technology(technologySlug);return categories.findAllByTechnologySlugAndStatusOrderBySortOrderAsc(technologySlug,VISIBLE).stream().map(c->new CategoryResponse(c.getId(),c.getSlug(),c.getName(),c.getSortOrder(),technologySlug)).toList();}
 public List<LessonSummaryResponse> lessons(String categorySlug){categories.findBySlugAndStatus(categorySlug,VISIBLE).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Category not found"));return lessons.findAllByCategorySlugAndStatusOrderByTitleAsc(categorySlug,VISIBLE).stream().map(l->new LessonSummaryResponse(l.getId(),l.getSlug(),l.getTitle(),l.getSummary(),l.getDifficulty(),l.getContentRevision())).toList();}
}
