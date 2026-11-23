package com.jia.academy.appregistry.service;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves a caller's registered app identity to its academy. The academy ID
 * is sourced exclusively from the registry, never from request parameters.
 */
@Service
public class AcademyRequestContextResolver {
    public static final String APP_CODE_HEADER = "X-Academy-App";
    public static final String PLATFORM_HEADER = "X-Academy-Platform";
    public static final String APPLICATION_ID_HEADER = "X-Academy-Application-Id";

    private final JdbcTemplate jdbc;

    public AcademyRequestContextResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Returns null only when all identity headers are absent, allowing existing
     * JIA clients to remain compatible while rollout is staged. Partial identity
     * headers are rejected to prevent ambiguous context.
     */
    public AcademyRequestContext resolve(String appCode, String platform, String applicationId) {
        boolean allAbsent = appCode == null && platform == null && applicationId == null;
        if (allAbsent) return null;
        if (blank(appCode) || blank(platform) || blank(applicationId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Academy app identity headers must be supplied together");
        }

        List<AcademyRequestContext> contexts = jdbc.query("""
                SELECT app.app_code, app.academy_id, app.platform, app.application_id
                FROM academy_apps app
                JOIN academies academy ON academy.id = app.academy_id
                WHERE app.app_code = ?
                  AND app.platform = ?
                  AND app.application_id = ?
                  AND app.status = 'ACTIVE'
                  AND academy.status = 'ACTIVE'
                """, (rs, row) -> new AcademyRequestContext(
                    rs.getString("app_code"),
                    rs.getObject("academy_id", UUID.class),
                    rs.getString("platform"),
                    rs.getString("application_id")),
                appCode.trim(), platform.trim().toUpperCase(java.util.Locale.ROOT),
                applicationId.trim());

        return contexts.stream().findFirst().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Registered active academy app identity not found"));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
