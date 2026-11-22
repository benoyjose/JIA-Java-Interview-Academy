package com.jia.academy.appregistry.service;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AcademyAppRegistryService {
    private final JdbcTemplate jdbc;

    public AcademyAppRegistryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public ReleasePolicy policy(String appCode, String platform, String applicationId, String installedVersion) {
        AppRow app = find(appCode);
        if (!app.platform().equals(platform) || !app.applicationId().equals(applicationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registered app identity not found");
        }
        if ("SUSPENDED".equals(app.status()) || "RETIRED".equals(app.status())) {
            throw new ResponseStatusException(HttpStatus.GONE, "This application is no longer supported");
        }
        String update = "CURRENT";
        if (installedVersion != null && app.minimumVersion() != null
                && compareVersions(installedVersion, app.minimumVersion()) < 0) update = "UPDATE_REQUIRED";
        else if (installedVersion != null && app.currentVersion() != null
                && compareVersions(installedVersion, app.currentVersion()) < 0) update = "UPDATE_AVAILABLE";
        return new ReleasePolicy(app.appCode(), app.platform(), app.applicationId(), app.displayName(),
                app.status(), app.minimumVersion(), app.currentVersion(), app.storeUrl(),
                app.releaseNotes(), app.releasedAt(), update);
    }

    @Transactional
    public ReleasePolicy update(String appCode, ReleaseUpdate request) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Release update is required");
        validateVersion(request.minimumSupportedVersion(), "Minimum supported version");
        validateVersion(request.currentVersion(), "Current version");
        if (request.minimumSupportedVersion() != null && request.currentVersion() != null
                && compareVersions(request.minimumSupportedVersion(), request.currentVersion()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Minimum supported version cannot exceed current version");
        }
        if (request.status() == null || !List.of("PLANNED", "ACTIVE", "SUSPENDED", "RETIRED").contains(request.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid app release status");
        }
        if (request.releaseNotes() != null && request.releaseNotes().length() > 5000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Release notes exceed 5000 characters");
        }
        validateStoreUrl(request.storeUrl());
        AppRow app = find(appCode);
        jdbc.update("""
                UPDATE academy_apps
                SET status=?, minimum_supported_version=?, current_version=?, store_url=?,
                    release_notes=?, released_at=CASE WHEN ?='ACTIVE' AND released_at IS NULL
                        THEN now() WHEN ?<>'ACTIVE' THEN NULL ELSE released_at END,
                    updated_at=now()
                WHERE app_code=?
                """, request.status(), request.minimumSupportedVersion(), request.currentVersion(),
                request.storeUrl(), request.releaseNotes(), request.status(), request.status(), appCode);
        AppRow updated = find(appCode);
        return new ReleasePolicy(updated.appCode(), updated.platform(), updated.applicationId(), updated.displayName(), updated.status(), updated.minimumVersion(), updated.currentVersion(), updated.storeUrl(), updated.releaseNotes(), updated.releasedAt(), "CURRENT");
    }

    private AppRow find(String appCode) {
        if (appCode == null || !appCode.matches("[A-Z][A-Z0-9_]{2,39}")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registered app not found");
        }
        return jdbc.query("""
                SELECT app_code,platform,application_id,display_name,status,
                    minimum_supported_version,current_version,store_url,release_notes,released_at
                FROM academy_apps WHERE app_code=?
                """, (rs, row) -> new AppRow(rs.getString("app_code"), rs.getString("platform"),
                rs.getString("application_id"), rs.getString("display_name"), rs.getString("status"),
                rs.getString("minimum_supported_version"), rs.getString("current_version"),
                rs.getString("store_url"), rs.getString("release_notes"),
                rs.getTimestamp("released_at") == null ? null : rs.getTimestamp("released_at").toInstant()),
                appCode).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registered app not found"));
    }

    private static void validateVersion(String version, String field) {
        if (version != null && !version.matches("\\d{1,5}\\.\\d{1,5}\\.\\d{1,5}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must use MAJOR.MINOR.PATCH");
        }
    }

    private static int compareVersions(String left, String right) {
        validateVersion(left, "Version");
        validateVersion(right, "Version");
        String[] a = left.split("\\."), b = right.split("\\.");
        for (int i = 0; i < 3; i++) {
            int compare = Integer.compare(Integer.parseInt(a[i]), Integer.parseInt(b[i]));
            if (compare != 0) return compare;
        }
        return 0;
    }

    private static void validateStoreUrl(String value) {
        if (value == null) return;
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || value.length() > 1000) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Store URL must be a valid HTTPS URL");
        }
    }

    private record AppRow(String appCode, String platform, String applicationId, String displayName,
            String status, String minimumVersion, String currentVersion, String storeUrl,
            String releaseNotes, Instant releasedAt) {}

    public record ReleasePolicy(String appCode, String platform, String applicationId, String displayName,
            String status, String minimumSupportedVersion, String currentVersion, String storeUrl,
            String releaseNotes, Instant releasedAt, String updateStatus) {}

    public record ReleaseUpdate(String status, String minimumSupportedVersion, String currentVersion,
            String storeUrl, String releaseNotes) {}
}
