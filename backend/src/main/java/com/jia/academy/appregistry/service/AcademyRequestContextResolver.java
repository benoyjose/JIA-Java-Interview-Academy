package com.jia.academy.appregistry.service;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Resolves registered app identity to academy scope; never trusts a caller's academy UUID. */
@Service
public class AcademyRequestContextResolver {
    public static final String APP_CODE_HEADER = "X-Academy-App";
    public static final String PLATFORM_HEADER = "X-Academy-Platform";
    public static final String APPLICATION_ID_HEADER = "X-Academy-Application-Id";

    private final JdbcTemplate jdbc;

    public AcademyRequestContextResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Headerless requests remain compatible during staged client rollout. */
    public AcademyRequestContext resolve(String appCode, String platform, String applicationId) {
        if (appCode == null && platform == null && applicationId == null) return null;
        if (blank(appCode) || blank(platform) || blank(applicationId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Academy app identity headers must be supplied together");
        }

        List<AppIdentity> matches = jdbc.query("""
                SELECT app.app_code, app.academy_id, app.platform, app.application_id,
                       app.status AS app_status, academy.status AS academy_status
                FROM academy_apps app
                JOIN academies academy ON academy.id = app.academy_id
                WHERE app.app_code = ? AND app.platform = ? AND app.application_id = ?
                """, (rs, row) -> new AppIdentity(rs.getString("app_code"),
                rs.getObject("academy_id", UUID.class), rs.getString("platform"),
                rs.getString("application_id"), rs.getString("app_status"),
                rs.getString("academy_status")), appCode.trim(),
                platform.trim().toUpperCase(java.util.Locale.ROOT), applicationId.trim());

        AppIdentity identity = matches.stream().findFirst().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Registered academy app identity not found"));
        if ("SUSPENDED".equals(identity.appStatus()) || "RETIRED".equals(identity.appStatus())
                || !"ACTIVE".equals(identity.academyStatus())) {
            throw new ResponseStatusException(HttpStatus.GONE, "This academy application is not active");
        }
        if (!"ACTIVE".equals(identity.appStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Academy application is not activated");
        }
        return new AcademyRequestContext(identity.appCode(), identity.academyId(),
                identity.platform(), identity.applicationId());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record AppIdentity(String appCode, UUID academyId, String platform,
            String applicationId, String appStatus, String academyStatus) {}
}
