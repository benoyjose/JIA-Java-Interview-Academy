package com.jia.academy.content.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class LessonImportDtos {
    private LessonImportDtos() {}
    public record BatchRequest(@NotNull Integer formatVersion,
                               @NotNull @Size(min=1,max=100) List<@Valid LessonRecord> lessons) {}
    public record LessonRecord(
            @NotBlank @Size(max=100) String categorySlug,
            @NotBlank @Size(max=120) String slug,
            @NotBlank @Size(max=200) String title,
            @Size(max=700) String summary,
            @NotBlank String difficulty,
            @NotNull @Valid Provenance provenance,
            @NotNull @Valid Content content,
            @Size(max=500) String changeNote) {}
    public record Provenance(@NotBlank String sourceType,
                             @NotBlank @Size(max=500) String sourceDescription,
                             @Size(max=1000) String attribution) {}
    public record Content(@NotNull Integer schemaVersion, @NotNull List<Object> blocks) {}
    public record ImportedLesson(String slug, String status, int revisionNumber) {}
    public record BatchResponse(int formatVersion, int importedCount, List<ImportedLesson> lessons) {}
}
