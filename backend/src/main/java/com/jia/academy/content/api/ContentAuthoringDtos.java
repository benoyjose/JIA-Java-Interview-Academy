package com.jia.academy.content.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class ContentAuthoringDtos {
    private ContentAuthoringDtos() {}

    public record LessonDraftRequest(
            @NotBlank @Size(max = 100) String categorySlug,
            @NotBlank @Size(max = 120) String slug,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 700) String summary,
            @NotBlank String difficulty,
            @NotBlank String sourceType,
            @NotBlank String contentJson,
            @Size(max = 500) String changeNote) {}

    public record LessonDraftResponse(
            UUID id, String slug, String title, String status, int revisionNumber) {}

    public record ReviewDecisionRequest(
            @NotBlank String decision,
            @Size(max = 2000) String comment) {}

    public record ReviewQueueItem(
            UUID taskId, UUID lessonId, UUID revisionId, String slug, String title,
            String submittedBy, String status, Instant submittedAt) {}
}
