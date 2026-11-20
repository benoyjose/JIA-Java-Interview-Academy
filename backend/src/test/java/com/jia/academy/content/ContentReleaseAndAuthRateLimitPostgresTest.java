package com.jia.academy.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jia.academy.auth.service.AuthRateLimitService;
import com.jia.academy.content.api.ContentAuthoringDtos.LessonDraftRequest;
import com.jia.academy.content.api.ContentAuthoringDtos.ReviewDecisionRequest;
import com.jia.academy.content.service.ContentAuthoringService;
import com.jia.academy.content.service.ContentReleaseService;
import com.jia.academy.content.service.LessonContentValidator;
import com.jia.academy.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ContentReleaseAndAuthRateLimitPostgresTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18.7-alpine")
                    .withDatabaseName("jia_release_test")
                    .withUsername("jia")
                    .withPassword("jia_test_only");

    private static JdbcTemplate jdbc;
    private static ContentAuthoringService authoring;
    private static ContentReleaseService releases;
    private static AuthRateLimitService rateLimits;
    private static CurrentUser editor;
    private static CurrentUser reviewer;
    private static CurrentUser admin;

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        ObjectMapper mapper = new ObjectMapper();
        authoring = new ContentAuthoringService(jdbc, mapper, new LessonContentValidator(mapper));
        releases = new ContentReleaseService(jdbc, mapper);
        rateLimits = new AuthRateLimitService(jdbc);
        editor = user("Editor", "EDITOR");
        reviewer = user("Reviewer", "REVIEWER");
        admin = user("Admin", "ADMIN");
    }

    @Test
    void contentReleaseKeepsExactPublishedRevisionAvailableAfterNewerPublication() {
        String slug = "release-snapshot-" + UUID.randomUUID();
        var draft = authoring.createDraft(editor, request(slug, "Version one"));
        UUID task = (UUID) authoring.submitForReview(editor, draft.id()).get("taskId");
        authoring.decideReview(reviewer, task, new ReviewDecisionRequest("APPROVE", "Reviewed"));
        authoring.publish(admin, draft.id());

        var release = releases.create(admin);
        var releasedItem = release.items().stream().filter(item -> item.slug().equals(slug)).findFirst().orElseThrow();
        assertThat(releasedItem.checksum()).hasSize(64);
        assertThat(releases.latest().version()).isEqualTo(release.version());

        authoring.updateDraft(editor, draft.id(), request(slug, "Version two"));
        UUID nextTask = (UUID) authoring.submitForReview(editor, draft.id()).get("taskId");
        authoring.decideReview(reviewer, nextTask, new ReviewDecisionRequest("APPROVE", "Reviewed v2"));
        authoring.publish(admin, draft.id());

        var immutableDownload = releases.lesson(release.version(), slug);
        assertThat(immutableDownload.title()).isEqualTo("Version one");
        assertThat(immutableDownload.contentRevision()).isEqualTo(1);
    }

    @Test
    void loginRateLimitAllowsTenAttemptsThenRejectsTheNextAttempt() {
        String ip = "198.51.100." + (10 + Math.abs(UUID.randomUUID().hashCode() % 200));
        for (int attempt = 0; attempt < 10; attempt++) {
            rateLimits.checkAndRecord(ip, "learner@example.test");
        }
        assertThatThrownBy(() -> rateLimits.checkAndRecord(ip, "learner@example.test"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Too many sign-in attempts");
    }

    private static CurrentUser user(String name, String role) {
        UUID id = UUID.randomUUID();
        String email = role.toLowerCase() + "-" + id + "@example.test";
        jdbc.update("INSERT INTO users(id,name,email,password_hash,status) VALUES (?,?,?,'test-hash','ACTIVE')",
                id, name, email);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code=?", id, role);
        return new CurrentUser(id, name, email, List.of(role), List.of());
    }

    private static LessonDraftRequest request(String slug, String title) {
        return new LessonDraftRequest(
                "java-language-fundamentals", slug, title, "Release snapshot test", "BEGINNER",
                "ADMIN_AUTHORED",
                "{\"schemaVersion\":1,\"blocks\":[{\"type\":\"markdown\",\"key\":\"overview\",\"body\":\"Example\"}]}",
                "Integration test");
    }
}
