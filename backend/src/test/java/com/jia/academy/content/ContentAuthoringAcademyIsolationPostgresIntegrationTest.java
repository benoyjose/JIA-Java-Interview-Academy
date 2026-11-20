package com.jia.academy.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jia.academy.content.api.ContentAuthoringDtos.LessonDraftRequest;
import com.jia.academy.content.service.ContentAuthoringService;
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
class ContentAuthoringAcademyIsolationPostgresIntegrationTest {
    private static final UUID JIA_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18.7-alpine")
                    .withDatabaseName("jia_authoring_scope_test")
                    .withUsername("jia")
                    .withPassword("jia_test_only");

    private static JdbcTemplate jdbc;
    private static ContentAuthoringService authoring;
    private static UUID secondAcademyId;
    private static CurrentUser jiaEditor;
    private static CurrentUser secondAcademyEditor;
    private static CurrentUser secondAcademyReviewer;

    @BeforeAll
    static void migrateAndSeed() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        authoring = new ContentAuthoringService(jdbc, new ObjectMapper(),
                new LessonContentValidator(new ObjectMapper()));

        secondAcademyId = UUID.randomUUID();
        jdbc.update("INSERT INTO academies(id,code,name,status) VALUES (?,'SCOPE','Scoped Academy','ACTIVE')",
                secondAcademyId);
        UUID technologyId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO technologies(id,academy_id,slug,name,sort_order,status)
                VALUES (?,?,'secure-java','Secure Java',1,'PUBLISHED')
                """, technologyId, secondAcademyId);
        UUID categoryId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO categories(id,technology_id,slug,name,sort_order,status)
                VALUES (?,?,'secure-java-basics','Secure Java Basics',1,'PUBLISHED')
                """, categoryId, technologyId);

        UUID jiaUserId = createUser("jia-editor@example.test");
        UUID scopedEditorId = createUser("scoped-editor@example.test");
        UUID scopedReviewerId = createUser("scoped-reviewer@example.test");
        // Move the scoped accounts' default membership from JIA to the second academy.
        jdbc.update("UPDATE academy_memberships SET is_default=FALSE,status='SUSPENDED' WHERE user_id IN (?,?) AND academy_id=?",
                scopedEditorId, scopedReviewerId, JIA_ID);
        jdbc.update("""
                INSERT INTO academy_memberships(user_id,academy_id,status,is_default)
                VALUES (?,?,'ACTIVE',TRUE),(?,?,'ACTIVE',TRUE)
                """, scopedEditorId, secondAcademyId, scopedReviewerId, secondAcademyId);

        jiaEditor = new CurrentUser(jiaUserId, "JIA Editor", "jia-editor@example.test",
                List.of("EDITOR"), List.of("content.create"), List.of(JIA_ID));
        secondAcademyEditor = new CurrentUser(scopedEditorId, "Scoped Editor", "scoped-editor@example.test",
                List.of("EDITOR"), List.of("content.create"), List.of(secondAcademyId));
        secondAcademyReviewer = new CurrentUser(scopedReviewerId, "Scoped Reviewer", "scoped-reviewer@example.test",
                List.of("REVIEWER"), List.of("content.review"), List.of(secondAcademyId));
    }

    @Test
    void authoringAndReviewQueuesCannotReadOrMutateAnotherAcademysContent() {
        var jiaDraft = authoring.createDraft(jiaEditor,
                request("java-language-fundamentals", "jia-scope-test", "JIA lesson"));
        var scopedDraft = authoring.createDraft(secondAcademyEditor,
                request("secure-java-basics", "scoped-scope-test", "Scoped lesson"));

        UUID jiaReview = (UUID) authoring.submitForReview(jiaEditor, jiaDraft.id()).get("taskId");
        UUID scopedReview = (UUID) authoring.submitForReview(secondAcademyEditor, scopedDraft.id()).get("taskId");

        var scopedLessons = authoring.adminLessons(secondAcademyEditor, null, null, 0, 25);
        assertThat(scopedLessons.items()).extracting(item -> item.id()).containsExactly(scopedDraft.id());

        var scopedReviews = authoring.reviewQueue(secondAcademyReviewer, null, 0, 25);
        assertThat(scopedReviews.items()).extracting(item -> item.taskId()).containsExactly(scopedReview);

        assertThatThrownBy(() -> authoring.authoringDetail(secondAcademyEditor, jiaDraft.id()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(404));
        assertThatThrownBy(() -> authoring.reviewDetail(secondAcademyReviewer, jiaReview))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(404));
        assertThatThrownBy(() -> authoring.updateDraft(secondAcademyEditor, jiaDraft.id(),
                request("secure-java-basics", "jia-scope-test", "Unauthorized edit")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(404));
    }

    private static UUID createUser(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,name,email,password_hash,status) VALUES (?,? ,?,'test-hash','ACTIVE')",
                id, email, email);
        return id;
    }

    private static LessonDraftRequest request(String category, String slug, String title) {
        return new LessonDraftRequest(category, slug, title, null, "BEGINNER", "ADMIN_AUTHORED",
                "{\"schemaVersion\":1,\"blocks\":[{\"type\":\"markdown\",\"key\":\"overview\",\"body\":\"Scoped content\"}]}",
                "Tenant isolation integration test");
    }
}
