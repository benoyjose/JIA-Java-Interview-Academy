package com.jia.academy.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jia.academy.content.api.ContentAuthoringDtos.LessonDraftRequest;
import com.jia.academy.content.api.ContentAuthoringDtos.ReviewDecisionRequest;
import com.jia.academy.content.service.ContentAuthoringService;
import com.jia.academy.security.CurrentUser;
import java.util.List;
import java.util.Map;
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
class ContentAuthoringPostgresIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18.7-alpine")
                    .withDatabaseName("jia_content_test")
                    .withUsername("jia")
                    .withPassword("jia_test_only");

    private static JdbcTemplate jdbc;
    private static ContentAuthoringService service;
    private static CurrentUser editor;
    private static CurrentUser reviewer;
    private static CurrentUser admin;

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        service = new ContentAuthoringService(jdbc, new ObjectMapper());
        editor = user("Editor", "editor@example.test", "EDITOR");
        reviewer = user("Reviewer", "reviewer@example.test", "REVIEWER");
        admin = user("Admin", "admin@example.test", "ADMIN");
    }

    @Test
    void editorCanCreateSubmitAndReviewerCanApproveBeforeAdminPublishes() {
        String slug = "content-authoring-" + UUID.randomUUID();
        var draft = service.createDraft(editor, request(slug, "Draft title"));
        assertThat(draft.status()).isEqualTo("DRAFT");
        assertThat(draft.revisionNumber()).isEqualTo(1);

        var submitted = service.submitForReview(editor, draft.id());
        UUID taskId = (UUID) submitted.get("taskId");
        assertThat(jdbc.queryForObject("SELECT revision_id IS NOT NULL FROM content_review_tasks WHERE id=?",
                Boolean.class, taskId)).isTrue();

        service.decideReview(reviewer, taskId, new ReviewDecisionRequest("APPROVE", "Looks good"));
        assertThat(jdbc.queryForObject("SELECT status FROM lessons WHERE id=?", String.class, draft.id()))
                .isEqualTo("APPROVED");

        service.publish(admin, draft.id());
        assertThat(jdbc.queryForObject("SELECT status FROM lessons WHERE id=?", String.class, draft.id()))
                .isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE entity_id=?",
                Integer.class, draft.id())).isEqualTo(4);
    }

    @Test
    void lessonCannotBePublishedBeforeApproval() {
        var draft = service.createDraft(editor,
                request("unapproved-" + UUID.randomUUID(), "Not approved"));
        assertThatThrownBy(() -> service.publish(admin, draft.id()))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM lessons WHERE id=?", String.class, draft.id()))
                .isEqualTo("DRAFT");
    }

    @Test
    void reviewerCanRequestChangesAndEditorCanSaveAnotherRevision() {
        var draft = service.createDraft(editor,
                request("revision-" + UUID.randomUUID(), "First draft"));
        UUID taskId = (UUID) service.submitForReview(editor, draft.id()).get("taskId");
        service.decideReview(reviewer, taskId, new ReviewDecisionRequest("REQUEST_CHANGES", "Add a code example"));
        assertThat(jdbc.queryForObject("SELECT status FROM lessons WHERE id=?", String.class, draft.id()))
                .isEqualTo("DRAFT");

        String slug = jdbc.queryForObject("SELECT slug FROM lessons WHERE id=?", String.class, draft.id());
        var updated = service.updateDraft(editor, draft.id(),
                request(slug, "Revised draft"));
        assertThat(updated.revisionNumber()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lesson_revisions WHERE lesson_id=?",
                Integer.class, draft.id())).isEqualTo(2);
    }

    @Test
    void authorCannotReviewTheirOwnSubmission() {
        var draft = service.createDraft(editor,
                request("self-review-" + UUID.randomUUID(), "Self review"));
        UUID taskId = (UUID) service.submitForReview(editor, draft.id()).get("taskId");
        assertThatThrownBy(() -> service.decideReview(editor, taskId,
                new ReviewDecisionRequest("APPROVE", "Not allowed")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value())
                        .isEqualTo(403));
        assertThat(jdbc.queryForObject("SELECT status FROM content_review_tasks WHERE id=?",
                String.class, taskId)).isEqualTo("PENDING");
    }

    private static CurrentUser user(String name, String email, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,name,email,password_hash,status) VALUES (?,?,?,'test-hash','ACTIVE')",
                id, name, email);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code=?", id, role);
        return new CurrentUser(id, name, email, List.of(role), List.of());
    }

    private static LessonDraftRequest request(String slug, String title) {
        return new LessonDraftRequest(
                "java-language-fundamentals", slug, title, "A test lesson", "BEGINNER",
                "ADMIN_AUTHORED",
                "{\"schemaVersion\":1,\"blocks\":[{\"type\":\"markdown\",\"key\":\"overview\",\"body\":\"Example\"}]}",
                "Integration test");
    }
}
