package com.jia.academy.content.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jia.academy.content.api.ContentReleaseResponse;
import com.jia.academy.content.api.ContentReleaseResponse.Item;
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
        long version = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version), 0) + 1 FROM content_releases", Long.class);
        List<Item> items = jdbc.query("""
                SELECT l.id,l.slug,l.title,r.revision_number,r.content_json::text AS content_json
                FROM lessons l
                JOIN lesson_revisions r ON r.id=l.published_revision_id
                WHERE l.status='PUBLISHED' AND l.published_revision_id IS NOT NULL
                ORDER BY l.slug
                """, (rs, row) -> new Item(
                rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("title"),
                rs.getInt("revision_number"), checksum(rs.getString("content_json"))));
        try {
            String manifest = mapper.writeValueAsString(new Manifest(version, items));
            Instant createdAt = Instant.now();
            jdbc.update("""
                    INSERT INTO content_releases(version,manifest_json,created_by,created_at)
                    VALUES (?,?::jsonb,?,?)
                    """, version, manifest, actor.id(), Timestamp.from(createdAt));
            return new ContentReleaseResponse(version, createdAt, items);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not create content release manifest", ex);
        }
    }

    @Transactional(readOnly = true)
    public ContentReleaseResponse latest() {
        return jdbc.query("""
                SELECT version,manifest_json::text,created_at
                FROM content_releases ORDER BY version DESC LIMIT 1
                """, (rs, row) -> response(rs.getLong("version"), rs.getString("manifest_json"),
                rs.getTimestamp("created_at").toInstant()))
                .stream().findFirst().orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "No content release is available"));
    }

    @Transactional(readOnly = true)
    public ContentReleaseResponse get(long version) {
        if (version < 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Content release not found");
        return jdbc.query("""
                SELECT version,manifest_json::text,created_at
                FROM content_releases WHERE version=?
                """, (rs, row) -> response(rs.getLong("version"), rs.getString("manifest_json"),
                rs.getTimestamp("created_at").toInstant()), version)
                .stream().findFirst().orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Content release not found"));
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
