package com.jia.academy.catalogue.api;
import java.util.UUID;
public final class CatalogueDtos {
 private CatalogueDtos() {}
 public record TechnologyResponse(UUID id,String slug,String name,String description,int sortOrder) {}
 public record CategoryResponse(UUID id,String slug,String name,int sortOrder,String technologySlug) {}
 public record LessonSummaryResponse(UUID id,String slug,String title,String summary,String difficulty,int contentRevision) {}
 public record LessonDetailResponse(UUID id,String slug,String title,String summary,String difficulty,int contentRevision,String sourceType,String contentJson) {}
}
