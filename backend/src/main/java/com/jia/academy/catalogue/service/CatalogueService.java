package com.jia.academy.catalogue.service;

import com.jia.academy.catalogue.api.CatalogueDtos.*;
import com.jia.academy.catalogue.domain.PublicationStatus;
import com.jia.academy.catalogue.repository.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly=true)
public class CatalogueService {
 private static final PublicationStatus VISIBLE=PublicationStatus.PUBLISHED;
 private final TechnologyRepository technologies;
 private final CategoryRepository categories;
 private final LessonRepository lessons;
 private final JdbcTemplate jdbc;

 public CatalogueService(TechnologyRepository technologies, CategoryRepository categories,
                         LessonRepository lessons, JdbcTemplate jdbc) {
  this.technologies=technologies;
  this.categories=categories;
  this.lessons=lessons;
  this.jdbc=jdbc;
 }
 public List<TechnologyResponse> technologies(){return technologies.findAllByStatusOrderBySortOrderAsc(VISIBLE).stream().map(t->new TechnologyResponse(t.getId(),t.getSlug(),t.getName(),t.getDescription(),t.getSortOrder())).toList();}
 public TechnologyResponse technology(String slug){var t=technologies.findBySlugAndStatus(slug,VISIBLE).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Technology not found"));return new TechnologyResponse(t.getId(),t.getSlug(),t.getName(),t.getDescription(),t.getSortOrder());}
 public List<CategoryResponse> categories(String technologySlug){technology(technologySlug);return categories.findAllByTechnologySlugAndStatusOrderBySortOrderAsc(technologySlug,VISIBLE).stream().map(c->new CategoryResponse(c.getId(),c.getSlug(),c.getName(),c.getSortOrder(),technologySlug)).toList();}
 public LessonDetailResponse lesson(String slug){
  return jdbc.query("""
      SELECT l.id,l.slug,r.title,r.summary,r.difficulty,r.revision_number,r.source_type,r.content_json::text
      FROM lessons l JOIN lesson_revisions r ON r.id=l.published_revision_id
      WHERE l.slug=? AND l.status='PUBLISHED'
      """,(rs,row)->new LessonDetailResponse(rs.getObject("id",java.util.UUID.class),
      rs.getString("slug"),rs.getString("title"),rs.getString("summary"),rs.getString("difficulty"),
      rs.getInt("revision_number"),rs.getString("source_type"),rs.getString("content_json")),slug)
      .stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Lesson not found"));
 }
 public List<LessonSummaryResponse> lessons(String categorySlug){
  categories.findBySlugAndStatus(categorySlug,VISIBLE).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Category not found"));
  return jdbc.query("""
      SELECT l.id,l.slug,r.title,r.summary,r.difficulty,r.revision_number
      FROM lessons l JOIN lesson_revisions r ON r.id=l.published_revision_id
      JOIN categories c ON c.id=l.category_id
      WHERE c.slug=? AND l.status='PUBLISHED'
      ORDER BY r.title ASC
      """,(rs,row)->new LessonSummaryResponse(rs.getObject("id",java.util.UUID.class),
      rs.getString("slug"),rs.getString("title"),rs.getString("summary"),rs.getString("difficulty"),
      rs.getInt("revision_number")),categorySlug);
 }
}
