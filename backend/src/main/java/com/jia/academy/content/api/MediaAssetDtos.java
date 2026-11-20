package com.jia.academy.content.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class MediaAssetDtos {
    private MediaAssetDtos() {}
    public record UploadRequest(UUID lessonId, @NotBlank @Size(max = 255) String fileName,
            @NotBlank @Size(max = 100) String contentType, @NotNull @Positive Long sizeBytes) {}
    public record UploadResponse(UUID id, String status, String method, String uploadUrl,
            Instant expiresAt, Map<String, String> requiredHeaders) {}
    public record MediaAssetResponse(UUID id, UUID lessonId, String fileName, String contentType,
            long sizeBytes, String status, Instant createdAt, Instant completedAt) {}
}
