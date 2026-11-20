package com.jia.academy.content;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jia.academy.content.api.MediaAssetDtos.UploadRequest;
import com.jia.academy.content.service.MediaAssetService;
import com.jia.academy.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class MediaAssetServiceTest {
    private MediaAssetService service;
    private CurrentUser editor;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        ObjectProvider<S3Presigner> presigners = mock(ObjectProvider.class);
        ObjectProvider<S3Client> clients = mock(ObjectProvider.class);
        when(presigners.getIfAvailable()).thenReturn(null);
        when(clients.getIfAvailable()).thenReturn(null);
        service = new MediaAssetService(mock(JdbcTemplate.class), presigners, clients, "", 10);
        editor = new CurrentUser(UUID.randomUUID(), "Editor", "editor@example.test",
                List.of("EDITOR"), List.of("content.create"), List.of(UUID.randomUUID()));
    }

    @Test
    void rejectsUnsupportedMediaTypesBeforeTouchingStorage() {
        assertThatThrownBy(() -> service.createUpload(editor,
                new UploadRequest(null, "payload.svg", "image/svg+xml", 100L)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                        ((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void enforcesPerTypeUploadSizeLimit() {
        assertThatThrownBy(() -> service.createUpload(editor,
                new UploadRequest(null, "large.png", "image/png", 25L * 1024 * 1024 + 1)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                        ((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    @Test
    void reportsStorageUnavailableWhenS3IsNotConfigured() {
        assertThatThrownBy(() -> service.createUpload(editor,
                new UploadRequest(null, "lesson.png", "image/png", 1024L)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                        ((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
