package com.jia.academy.content.service;

import com.jia.academy.security.CurrentUser;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminOperationsService {
    private static final Set<String> USER_STATUSES = Set.of("ACTIVE", "LOCKED", "DISABLED");
    private static final Set<String> ROLES = Set.of("LEARNER", "EDITOR", "REVIEWER", "ADMIN");
    private static final Map<String, Set<String>> QUESTION_TRANSITIONS = Map.of(
            "DRAFT", Set.of("IN_REVIEW", "ARCHIVED"),
            "IN_REVIEW", Set.of("APPROVED", "DRAFT", "ARCHIVED"),
            "APPROVED", Set.of("PUBLISHED", "DRAFT", "ARCHIVED"),
            "PUBLISHED", Set.of("ARCHIVED"),
            "ARCHIVED", Set.of("DRAFT"));

    private final JdbcTemplate jdbc;

    public AdminOperationsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PageResult<UserItem> users(String query, int page, int size) {
        validatePage(page, size);
        String search = normalize(query);
        String pattern = search == null ? null : "%" + search + "%";
        long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM users u
                WHERE CAST(? AS text) IS NULL OR lower(u.name) LIKE lower(?)
                   OR lower(COALESCE(u.email,'')) LIKE lower(?)
                """, Long.class, search, pattern, pattern);
        List<UserItem> items = jdbc.query("""
                SELECT u.id,u.name,u.email,u.status,u.created_at,
                       COALESCE(array_agg(DISTINCT r.code) FILTER (WHERE r.code IS NOT NULL), ARRAY[]::varchar[]) AS roles
                FROM users u LEFT JOIN user_roles ur ON ur.user_id=u.id
                LEFT JOIN roles r ON r.id=ur.role_id
                WHERE CAST(? AS text) IS NULL OR lower(u.name) LIKE lower(?)
                   OR lower(COALESCE(u.email,'')) LIKE lower(?)
                GROUP BY u.id ORDER BY u.created_at DESC,u.id
                LIMIT ? OFFSET ?
                """, (rs, row) -> new UserItem(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("email"), rs.getString("status"),
                List.of((String[]) rs.getArray("roles").getArray()), rs.getTimestamp("created_at").toInstant()),
                search, pattern, pattern, size, (long) page * size);
        return new PageResult<>(items, page, size, total, totalPages(total, size));
    }

    @Transactional
    public UserItem updateUser(CurrentUser actor, UUID userId, UserUpdate request) {
        if (request == null || request.status() == null || request.roles() == null || request.roles().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status and at least one role are required");
        }
        String status = request.status().trim().toUpperCase();
        List<String> roles = request.roles().stream().map(value -> value.trim().toUpperCase()).distinct().sorted().toList();
        if (!USER_STATUSES.contains(status) || !ROLES.containsAll(roles)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported user status or role");
        }
        if (actor.id().equals(userId) && !"ACTIVE".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot disable or lock your own account");
        }
        List<String> existingRoles = jdbc.queryForList("""
                SELECT r.code FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=?
                """, String.class, userId);
        if (existingRoles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        if (existingRoles.contains("ADMIN") && !roles.contains("ADMIN")) {
            Integer activeAdmins = jdbc.queryForObject("""
                    SELECT COUNT(DISTINCT u.id) FROM users u
                    JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id
                    WHERE r.code='ADMIN' AND u.status='ACTIVE'
                    """, Integer.class);
            if (activeAdmins != null && activeAdmins <= 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The final active administrator cannot be demoted");
            }
        }
        jdbc.update("UPDATE users SET status=?,updated_at=now() WHERE id=?", status, userId);
        jdbc.update("DELETE FROM user_roles WHERE user_id=?", userId);
        for (String role : roles) {
            jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code=?",
                    userId, role);
        }
        audit(actor, "ADMIN_USER_UPDATED", "USER", userId);
        return jdbc.query("""
                SELECT u.id,u.name,u.email,u.status,u.created_at,
                       COALESCE(array_agg(DISTINCT r.code) FILTER (WHERE r.code IS NOT NULL), ARRAY[]::varchar[]) AS roles
                FROM users u LEFT JOIN user_roles ur ON ur.user_id=u.id
                LEFT JOIN roles r ON r.id=ur.role_id WHERE u.id=?
                GROUP BY u.id
                """, (rs, row) -> new UserItem(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("email"), rs.getString("status"),
                List.of((String[]) rs.getArray("roles").getArray()), rs.getTimestamp("created_at").toInstant()), userId)
                .stream().findFirst().orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    @Transactional(readOnly = true)
    public PageResult<QuestionItem> questions(String query, String status, String difficulty, int page, int size) {
        validatePage(page, size);
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        String normalizedDifficulty = difficulty == null || difficulty.isBlank() ? null : difficulty.trim().toUpperCase();
        if (normalizedStatus != null && !Set.of("DRAFT","IN_REVIEW","APPROVED","PUBLISHED","ARCHIVED").contains(normalizedStatus))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported question status");
        if (normalizedDifficulty != null && !Set.of("BEGINNER","NOVICE","PROFESSIONAL","EXPERT").contains(normalizedDifficulty))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported difficulty");
        String search = normalize(query);
        String pattern = search == null ? null : "%" + search + "%";
        long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM questions q LEFT JOIN categories c ON c.id=q.category_id
                JOIN technologies t ON t.id=q.technology_id
                LEFT JOIN LATERAL (SELECT prompt_json FROM question_revisions qr
                    WHERE qr.question_id=q.id ORDER BY qr.revision_number DESC LIMIT 1) rev ON TRUE
                WHERE (CAST(? AS text) IS NULL OR q.status=?) AND (CAST(? AS text) IS NULL OR q.difficulty=?)
                  AND (CAST(? AS text) IS NULL OR lower(q.slug) LIKE lower(?) OR lower(c.name) LIKE lower(?)
                       OR lower(t.name) LIKE lower(?) OR lower(rev.prompt_json->>'text') LIKE lower(?))
                """, Long.class, normalizedStatus, normalizedStatus, normalizedDifficulty, normalizedDifficulty,
                search, pattern, pattern, pattern, pattern);
        List<QuestionItem> items = jdbc.query("""
                SELECT q.id,q.slug,q.question_type,q.difficulty,q.status,q.current_revision,
                       c.name AS category_name,c.slug AS category_slug,t.name AS technology_name,
                       COALESCE(rev.prompt_json->>'text','') AS prompt_text,q.updated_at
                FROM questions q LEFT JOIN categories c ON c.id=q.category_id
                JOIN technologies t ON t.id=q.technology_id
                LEFT JOIN LATERAL (SELECT prompt_json FROM question_revisions qr
                    WHERE qr.question_id=q.id ORDER BY qr.revision_number DESC LIMIT 1) rev ON TRUE
                WHERE (CAST(? AS text) IS NULL OR q.status=?) AND (CAST(? AS text) IS NULL OR q.difficulty=?)
                  AND (CAST(? AS text) IS NULL OR lower(q.slug) LIKE lower(?) OR lower(c.name) LIKE lower(?)
                       OR lower(t.name) LIKE lower(?) OR lower(rev.prompt_json->>'text') LIKE lower(?))
                ORDER BY q.updated_at DESC,q.id LIMIT ? OFFSET ?
                """, (rs, row) -> new QuestionItem(rs.getObject("id", UUID.class), rs.getString("slug"),
                rs.getString("question_type"), rs.getString("difficulty"), rs.getString("status"),
                rs.getInt("current_revision"), rs.getString("category_name"), rs.getString("category_slug"),
                rs.getString("technology_name"), rs.getString("prompt_text"), rs.getTimestamp("updated_at").toInstant()),
                normalizedStatus, normalizedStatus, normalizedDifficulty, normalizedDifficulty,
                search, pattern, pattern, pattern, pattern, size, (long) page * size);
        return new PageResult<>(items, page, size, total, totalPages(total, size));
    }

    @Transactional
    public QuestionItem transitionQuestion(CurrentUser actor, UUID questionId, String nextStatus) {
        String target = nextStatus == null ? "" : nextStatus.trim().toUpperCase();
        var rows = jdbc.query("""
                SELECT q.status,q.current_revision,q.academy_id
                FROM questions q WHERE q.id=? FOR UPDATE
                """, (rs, row) -> new QuestionState(rs.getString("status"), rs.getInt("current_revision"),
                rs.getObject("academy_id", UUID.class)), questionId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found");
        QuestionState state = rows.getFirst();
        String requiredPermission = "APPROVED".equals(target)
                || ("DRAFT".equals(target) && "IN_REVIEW".equals(state.status()))
                ? "content.review"
                : ("PUBLISHED".equals(target) || ("ARCHIVED".equals(target) && "PUBLISHED".equals(state.status()))
                ? "content.publish" : "content.create");
        if (!actor.permissions().contains(requiredPermission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing permission: " + requiredPermission);
        }
        if (!QUESTION_TRANSITIONS.getOrDefault(state.status(), Set.of()).contains(target)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Question cannot transition from " + state.status() + " to " + target);
        }
        jdbc.update("UPDATE questions SET status=?,updated_at=now() WHERE id=?", target, questionId);
        if ("APPROVED".equals(target)) {
            jdbc.update("""
                    UPDATE question_revisions SET reviewed_by=?,reviewed_at=now()
                    WHERE question_id=? AND revision_number=?
                    """, actor.id(), questionId, state.revision());
        }
        jdbc.update("""
                INSERT INTO audit_events(id,actor_user_id,academy_id,action,entity_type,entity_id,metadata_json)
                VALUES (?,?,?,?,?, ?,jsonb_build_object('from',?,'to',?))
                """, UUID.randomUUID(), actor.id(), state.academyId(), "QUESTION_STATUS_CHANGED",
                "QUESTION", questionId, state.status(), target);
        return questions(null, null, null, 0, 100).items().stream().filter(item -> item.id().equals(questionId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));
    }

    private void audit(CurrentUser actor, String action, String entityType, UUID id) {
        jdbc.update("""
                INSERT INTO audit_events(id,actor_user_id,action,entity_type,entity_id,metadata_json)
                VALUES (?,?,?,?,?,jsonb_build_object('source','admin-operations'))
                """, UUID.randomUUID(), actor.id(), action, entityType, id);
    }
    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
    }
    private static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > 120) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search text is too long");
        return value.trim();
    }
    private static int totalPages(long total, int size) { return (int) Math.ceil((double) total / size); }

    private record QuestionState(String status, int revision, UUID academyId) {}
    public record PageResult<T>(List<T> items, int page, int size, long totalItems, int totalPages) {}
    public record UserItem(UUID id, String name, String email, String status, List<String> roles, Instant createdAt) {}
    public record UserUpdate(String status, List<String> roles) {}
    public record QuestionItem(UUID id, String slug, String questionType, String difficulty, String status,
            int revisionNumber, String categoryName, String categorySlug, String technologyName,
            String promptText, Instant updatedAt) {}
}
