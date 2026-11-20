package com.jia.academy.content.service;

import com.jia.academy.content.api.MediaAssetDtos.MediaAssetResponse;
import com.jia.academy.content.api.MediaAssetDtos.UploadRequest;
import com.jia.academy.content.api.MediaAssetDtos.UploadResponse;
import com.jia.academy.security.CurrentUser;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
public class MediaAssetService {
    private static final long MAX_IMAGE_OR_DOCUMENT_BYTES = 25L * 1024 * 1024;
    private static final long MAX_VIDEO_BYTES = 250L * 1024 * 1024;
    private static final List<String> ALLOWED_TYPES =
            List.of("image/jpeg", "image/png", "image/webp", "application/pdf", "video/mp4");

    private final JdbcTemplate jdbc;
    private final ObjectProvider<S3Presigner> presigners;
    private final ObjectProvider<S3Client> clients;
    private final String bucket;
    private final int uploadUrlMinutes;

    public MediaAssetService(JdbcTemplate jdbc, ObjectProvider<S3Presigner> presigners,
            ObjectProvider<S3Client> clients, @Value("${jia.media.s3.bucket:}") String bucket,
            @Value("${jia.media.s3.upload-url-minutes:10}") int uploadUrlMinutes) {
        this.jdbc = jdbc;
        this.presigners = presigners;
        this.clients = clients;
        this.bucket = bucket;
        this.uploadUrlMinutes = uploadUrlMinutes;
    }

    @Transactional
    public UploadResponse createUpload(CurrentUser actor, UploadRequest request) {
        validateRequest(request);
        S3Presigner presigner = storagePresigner();
        UUID academyId = primaryAcademyId(actor);
        if (request.lessonId() != null) {
            Integer exists = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM lessons l
                    JOIN categories c ON c.id=l.category_id
                    JOIN technologies t ON t.id=c.technology_id
                    WHERE l.id=? AND t.academy_id=?
                    """, Integer.class, request.lessonId(), academyId);
            if (exists == null || exists == 0)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found");
        }

        UUID mediaId = UUID.randomUUID();
        String fileName = safeFileName(request.fileName());
        String objectKey = academyId + "/" + mediaId;
        jdbc.update("""
                INSERT INTO media_assets(id,academy_id,lesson_id,object_key,file_name,content_type,
                    size_bytes,status,uploaded_by)
                VALUES (?,?,?,?,?,?,?,'PENDING',?)
                """, mediaId, academyId, request.lessonId(), objectKey, fileName,
                request.contentType(), request.sizeBytes(), actor.id());

        Duration duration = Duration.ofMinutes(uploadUrlMinutes);
        var put = PutObjectRequest.builder().bucket(bucket).key(objectKey)
                .contentType(request.contentType()).build();
        var signed = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(duration).putObjectRequest(put).build());
        return new UploadResponse(mediaId, "PENDING", "PUT", signed.url().toString(),
                Instant.now().plus(duration), Map.of("Content-Type", request.contentType()));
    }

    @Transactional
    public MediaAssetResponse completeUpload(CurrentUser actor, UUID mediaId) {
        UUID academyId = primaryAcademyId(actor);
        var rows = jdbc.query("""
                SELECT id,lesson_id,object_key,file_name,content_type,size_bytes,status,created_at,completed_at
                FROM media_assets WHERE id=? AND academy_id=? AND uploaded_by=?
                """, (rs, row) -> new AssetRow(
                new MediaAssetResponse(rs.getObject("id", UUID.class), rs.getObject("lesson_id", UUID.class),
                    rs.getString("file_name"), rs.getString("content_type"), rs.getLong("size_bytes"),
                    rs.getString("status"), rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant()),
                rs.getString("object_key")), mediaId, academyId, actor.id());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found");
        AssetRow row = rows.getFirst();
        MediaAssetResponse asset = row.asset();
        if ("READY".equals(asset.status())) return asset;

        try {
            var head = storageClient().headObject(HeadObjectRequest.builder()
                    .bucket(bucket).key(row.objectKey()).build());
            if (head.contentLength() == null || head.contentLength() != asset.sizeBytes()
                    || !asset.contentType().equalsIgnoreCase(head.contentType())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Uploaded object size or content type does not match the declaration");
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Uploaded object could not be verified; retry after upload completes");
        }

        jdbc.update("UPDATE media_assets SET status='READY',completed_at=now() WHERE id=? AND status='PENDING'",
                mediaId);
        jdbc.update("""
                INSERT INTO audit_events(id,actor_user_id,academy_id,action,entity_type,entity_id,metadata_json)
                VALUES (?,?,?,'MEDIA_ASSET_UPLOADED','MEDIA_ASSET',?,jsonb_build_object('contentType',?,'sizeBytes',?))
                """, UUID.randomUUID(), actor.id(), academyId, mediaId, asset.contentType(), asset.sizeBytes());
        return new MediaAssetResponse(asset.id(), asset.lessonId(), asset.fileName(), asset.contentType(),
                asset.sizeBytes(), "READY", asset.createdAt(), Instant.now());
    }

    @Transactional(readOnly = true)
    public MediaAssetResponse get(CurrentUser actor, UUID mediaId) {
        return jdbc.query("""
                SELECT id,lesson_id,file_name,content_type,size_bytes,status,created_at,completed_at
                FROM media_assets WHERE id=? AND academy_id=?
                """, (rs, row) -> new MediaAssetResponse(
                rs.getObject("id", UUID.class), rs.getObject("lesson_id", UUID.class),
                rs.getString("file_name"), rs.getString("content_type"), rs.getLong("size_bytes"),
                rs.getString("status"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant()),
                mediaId, primaryAcademyId(actor)).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found"));
    }

    private void validateRequest(UploadRequest request) {
        if (request == null || request.fileName() == null || request.contentType() == null
                || request.sizeBytes() == null || request.sizeBytes() <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A file name, content type and positive size are required");
        if (!ALLOWED_TYPES.contains(request.contentType().toLowerCase()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported media content type");
        long limit = "video/mp4".equals(request.contentType().toLowerCase())
                ? MAX_VIDEO_BYTES : MAX_IMAGE_OR_DOCUMENT_BYTES;
        if (request.sizeBytes() > limit)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Media file exceeds the allowed size");
        if (uploadUrlMinutes < 1 || uploadUrlMinutes > 30 || bucket.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Media object storage is not configured");
    }

    private S3Presigner storagePresigner() {
        S3Presigner value = presigners.getIfAvailable();
        if (value == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Media object storage is not configured");
        return value;
    }

    private S3Client storageClient() {
        S3Client value = clients.getIfAvailable();
        if (value == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Media object storage is not configured");
        return value;
    }

    private static UUID primaryAcademyId(CurrentUser actor) {
        if (actor == null || actor.academyIds() == null || actor.academyIds().isEmpty())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "An active academy membership is required");
        return actor.academyIds().getFirst();
    }

    private static String safeFileName(String fileName) {
        String basename = fileName.replace('\\', '/');
        basename = basename.substring(basename.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "").trim();
        if (basename.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file name");
        return basename.substring(0, Math.min(255, basename.length()));
    }

    private record AssetRow(MediaAssetResponse asset, String objectKey) {}
}
