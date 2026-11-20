package com.jia.academy.content.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jia.academy.content.api.ContentReleaseResponse;
import com.jia.academy.content.api.ContentReleaseResponse.Item;
import com.jia.academy.catalogue.api.CatalogueDtos.LessonDetailResponse;
import com.jia.academy.security.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ContentReleaseService {
    private static final UUID JIA_ACADEMY = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ContentReleaseService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public ContentReleaseResponse create(CurrentUser actor) {
        // Serialize release creation so concurrent publishers cannot allocate the same version.
        jdbc.execute("SELECT pg_advisory_xact_lock(71823014)");
        UUID academyId = academyId(actor);
        long version = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version), 0) + 1 FROM content_releases WHERE academy_id=?", Long.class, academyId);
        List<Item> items = jdbc.query("""
                SELECT l.id,l.slug,l.title,r.revision_number,r.content_json::text AS content_json
                FROM lessons l
                JOIN categories c ON c.id=l.category_id
                JOIN technologies t ON t.id=c.technology_id
                JOIN lesson_revisions r ON r.id=l.published_revision_id
                WHERE t.academy_id=? AND l.status='PUBLISHED' AND l.published_revision_id IS NOT NULL
                ORDER BY l.slug
                """, (rs, row) -> new Item(
                rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("title"),
                rs.getInt("revision_number"), checksum(rs.getString("content_json"))), academyId);
        try {
            String manifest = mapper.writeValueAsString(new Manifest(version, items));
            Instant createdAt = Instant.now();
            jdbc.update("""
                    INSERT INTO content_releases(academy_id,version,manifest_json,created_by,created_at)
                    VALUES (?,?,?::jsonb,?,?)
                    """, academyId, version, manifest, actor.id(), Timestamp.from(createdAt));
            return new ContentReleaseResponse(version, createdAt, items);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not create content release manifest", ex);
        }
    }

    @Transactional(readOnly = true)
    public ContentReleaseResponse latest() {
        return latest(null);
    }

    @Transactional(readOnly = true)
    public ContentReleaseResponse latest(CurrentUser user) {
        UUID academyId = academyId(user);
        return jdbc.query("""
                SELECT version,manifest_json::text,created_at
                FROM content_releases WHERE academy_id=? ORDER BY version DESC LIMIT 1
                """, (rs, row) -> response(rs.getLong("version"), rs.getString("manifest_json"),
                rs.getTimestamp("created_at").toInstant()), academyId)
                .stream().findFirst().orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "No content release is available"));
    }

    @Transactional(readOnly = true)
    public ContentReleaseResponse get(long version) {
        return get(version, null);
    }

    @Transactional(readOnly = true)
    public ContentReleaseResponse get(long version, CurrentUser user) {
        if (version < 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Content release not found");
        UUID academyId = academyId(user);
        return jdbc.query("""
                SELECT version,manifest_json::text,created_at
                FROM content_releases WHERE academy_id=? AND version=?
                """, (rs, row) -> response(rs.getLong("version"), rs.getString("manifest_json"),
                rs.getTimestamp("created_at").toInstant()), academyId, version)
                .stream().findFirst().orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Content release not found"));
    }

    @Transactional(readOnly = true)
    public LessonDetailResponse lesson(long version, String slug) {
        return lesson(version, slug, null);
    }

    @Transactional(readOnly = true)
    public LessonDetailResponse lesson(long version, String slug, CurrentUser user) {
        UUID academyId = academyId(user);
        return jdbc.query("""
                SELECT l.id,l.slug,r.title,r.summary,r.difficulty,r.revision_number,
                       r.source_type,r.content_json::text AS content_json
                FROM content_releases cr
                CROSS JOIN LATERAL jsonb_array_elements(cr.manifest_json->'items') AS item(value)
                JOIN lessons l ON l.id=(item.value->>'id')::uuid
                JOIN categories c ON c.id=l.category_id
                JOIN technologies t ON t.id=c.technology_id
                JOIN lesson_revisions r ON r.lesson_id=l.id
                    AND r.revision_number=(item.value->>'contentRevision')::integer
                WHERE cr.academy_id=? AND t.academy_id=? AND cr.version=? AND l.slug=?
                """, (rs, row) -> new LessonDetailResponse(
                rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("title"),
                rs.getString("summary"), rs.getString("difficulty"), rs.getInt("revision_number"),
                rs.getString("source_type"), rs.getString("content_json")), academyId, academyId, version, slug)
                .stream().findFirst().orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson is not part of this content release"));
    }

    private UUID academyId(CurrentUser user) {
        if (user == null) return JIA_ACADEMY;
        return jdbc.query("""
                SELECT am.academy_id
                FROM academy_memberships am
                JOIN academies a ON a.id=am.academy_id
                WHERE am.user_id=? AND am.status='ACTIVE' AND a.status='ACTIVE'
                ORDER BY am.is_default DESC, am.joined_at, am.academy_id
                LIMIT 1
                """, (rs, row) -> rs.getObject("academy_id", UUID.class), user.id())
                .stream().findFirst().orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.FORBIDDEN, "An active academy membership is required"));
    }

    private ContentReleaseResponse response(long version, String json, Instant createdAt) {
        try {
            Manifest manifest = mapper.readValue(json, Manifest.class);
            return new ContentReleaseResponse(version, createdAt, manifest.items());
        } catch (Exception ex) {
            throw new IllegalStateException("Stored content release manifest is invalid", ex);
        }
    }

    private static String checksum(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private record Manifest(long version, List<Item> items) {}
}
