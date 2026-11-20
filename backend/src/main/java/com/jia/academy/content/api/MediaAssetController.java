package com.jia.academy.content.api;

import com.jia.academy.content.api.MediaAssetDtos.MediaAssetResponse;
import com.jia.academy.content.api.MediaAssetDtos.UploadRequest;
import com.jia.academy.content.api.MediaAssetDtos.UploadResponse;
import com.jia.academy.content.service.MediaAssetService;
import com.jia.academy.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/media")
@PreAuthorize("hasAuthority('content.create')")
public class MediaAssetController {
    private final MediaAssetService media;
    public MediaAssetController(MediaAssetService media) { this.media = media; }

    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResponse createUpload(@AuthenticationPrincipal CurrentUser actor,
            @Valid @RequestBody UploadRequest request) {
        return media.createUpload(actor, request);
    }

    @PostMapping("/{mediaId}/complete")
    public MediaAssetResponse complete(@AuthenticationPrincipal CurrentUser actor,
            @PathVariable UUID mediaId) {
        return media.completeUpload(actor, mediaId);
    }

    @GetMapping("/{mediaId}")
    public MediaAssetResponse get(@AuthenticationPrincipal CurrentUser actor,
            @PathVariable UUID mediaId) {
        return media.get(actor, mediaId);
    }
}
