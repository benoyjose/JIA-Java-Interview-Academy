package com.jia.academy.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jia.academy.content.api.ContentAuthoringDtos.LessonDraftRequest;
import com.jia.academy.content.api.ContentAuthoringDtos.ReviewDecisionRequest;
import com.jia.academy.content.api.LessonImportDtos.BatchRequest;
import com.jia.academy.content.api.LessonImportDtos.Content;
import com.jia.academy.content.api.LessonImportDtos.LessonRecord;
import com.jia.academy.content.api.LessonImportDtos.Provenance;
import com.jia.academy.content.service.LessonImportService;
import com.jia.academy.content.service.ContentAuthoringService;
import com.jia.academy.content.service.LessonContentValidator;
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
    private static LessonImportService imports;
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
        var validator = new LessonContentValidator(mapper);
        service = new ContentAuthoringService(jdbc, mapper, validator);
        imports = new LessonImportService(jdbc, mapper, validator, service);
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
    void publishedLessonStaysLiveUntilApprovedRevisionIsPromoted() {
        String slug = "published-revision-" + UUID.randomUUID();
        var initial = service.createDraft(editor, request(slug, "Published title v1"));
        UUID initialTask = (UUID) service.submitForReview(editor, initial.id()).get("taskId");
        service.decideReview(reviewer, initialTask, new ReviewDecisionRequest("APPROVE", "Initial approval"));
        service.publish(admin, initial.id());

        UUID originalPublishedRevision = jdbc.queryForObject(
                "SELECT published_revision_id FROM lessons WHERE id=?",
                UUID.class, initial.id());
        var revised = service.updateDraft(editor, initial.id(),
                request(slug, "Published title v2"));
        assertThat(revised.revisionNumber()).isEqualTo(2);
        UUID draftRevision = jdbc.queryForObject(
                "SELECT draft_revision_id FROM lessons WHERE id=?", UUID.class, initial.id());
        assertThat(draftRevision).isNotEqualTo(originalPublishedRevision);
        assertThat(jdbc.queryForObject(
                "SELECT title FROM lesson_revisions WHERE id=(SELECT published_revision_id FROM lessons WHERE id=?)",
                String.class, initial.id())).isEqualTo("Published title v1");
        assertThat(jdbc.queryForObject("SELECT status FROM lessons WHERE id=?", String.class, initial.id()))
                .isEqualTo("PUBLISHED");

        UUID revisionTask = (UUID) service.submitForReview(editor, initial.id()).get("taskId");
        service.decideReview(reviewer, revisionTask, new ReviewDecisionRequest("APPROVE", "Revision approved"));
        assertThat(jdbc.queryForObject("SELECT status FROM lessons WHERE id=?", String.class, initial.id()))
                .isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT published_revision_id FROM lessons WHERE id=?",
                UUID.class, initial.id())).isEqualTo(originalPublishedRevision);

        service.publish(admin, initial.id());
        assertThat(jdbc.queryForObject("SELECT published_revision_id FROM lessons WHERE id=?",
                UUID.class, initial.id())).isEqualTo(draftRevision);
        assertThat(jdbc.queryForObject("SELECT draft_revision_id FROM lessons WHERE id=?",
                UUID.class, initial.id())).isNull();
        assertThat(jdbc.queryForObject("SELECT title FROM lesson_revisions WHERE id=(SELECT published_revision_id FROM lessons WHERE id=?)",
                String.class, initial.id())).isEqualTo("Published title v2");
    }

    @Test
    void adminCanReloadDraftAndReviewerCanInspectExactSubmittedRevision() throws Exception {
        String slug = "admin-read-" + UUID.randomUUID();
        var draft = service.createDraft(editor, request(slug, "Read API draft"));
        var detail = service.authoringDetail(draft.id());
        assertThat(detail.title()).isEqualTo("Read API draft");
        assertThat(new ObjectMapper().readTree(detail.contentJson()).path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(detail.editingDraft()).isTrue();

        var list = service.adminLessons("DRAFT", null, 0, 25).items();
        assertThat(list).anySatisfy(item -> {
            assertThat(item.id()).isEqualTo(draft.id());
            assertThat(item.status()).isEqualTo("DRAFT");
        });

        UUID taskId = (UUID) service.submitForReview(editor, draft.id()).get("taskId");
        var review = service.reviewDetail(taskId);
        assertThat(review.lessonId()).isEqualTo(draft.id());
        assertThat(review.revisionId()).isNotNull();
        assertThat(review.title()).isEqualTo("Read API draft");
        assertThat(new ObjectMapper().readTree(review.contentJson()).path("blocks").isArray()).isTrue();
        assertThat(review.revisionNumber()).isEqualTo(1);
    }

    @Test
    void adminLessonAndReviewQueuesSupportBoundedSearchAndPagination() {
        String query = "page-pagination-" + UUID.randomUUID();
        var first = service.createDraft(editor, request(query + "-a", "Pagination lesson A"));
        var second = service.createDraft(editor, request(query + "-b", "Pagination lesson B"));
        service.submitForReview(editor, first.id());
        service.submitForReview(editor, second.id());

        var lessonsPage0 = service.adminLessons(null, query, 0, 1);
        var lessonsPage1 = service.adminLessons(null, query, 1, 1);
        assertThat(lessonsPage0.totalItems()).isEqualTo(2);
        assertThat(lessonsPage0.totalPages()).isEqualTo(2);
        assertThat(lessonsPage0.items()).hasSize(1);
        assertThat(lessonsPage1.items()).hasSize(1);
        assertThat(lessonsPage0.items().getFirst().id())
                .isNotEqualTo(lessonsPage1.items().getFirst().id());

        var reviewsPage0 = service.reviewQueue(query, 0, 1);
        var reviewsPage1 = service.reviewQueue(query, 1, 1);
        assertThat(reviewsPage0.totalItems()).isEqualTo(2);
        assertThat(reviewsPage0.totalPages()).isEqualTo(2);
        assertThat(reviewsPage0.items()).hasSize(1);
        assertThat(reviewsPage1.items()).hasSize(1);
        assertThat(reviewsPage0.items().getFirst().taskId())
                .isNotEqualTo(reviewsPage1.items().getFirst().taskId());

        assertThatThrownBy(() -> service.adminLessons(null, null, -1, 25))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.reviewQueue(null, 0, 101))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void authoringDetailReturnsPendingRevisionForPublishedLessonWithoutChangingLiveRevision() {
        String slug = "admin-published-read-" + UUID.randomUUID();
        var initial = service.createDraft(editor, request(slug, "Published v1"));
        UUID initialTask = (UUID) service.submitForReview(editor, initial.id()).get("taskId");
        service.decideReview(reviewer, initialTask, new ReviewDecisionRequest("APPROVE", "Initial approval"));
        service.publish(admin, initial.id());

        service.updateDraft(editor, initial.id(), request(slug, "Draft v2"));
        var detail = service.authoringDetail(initial.id());
        assertThat(detail.title()).isEqualTo("Draft v2");
        assertThat(detail.editingDraft()).isTrue();
        assertThat(detail.publishedRevision()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT title FROM lesson_revisions WHERE id=(SELECT published_revision_id FROM lessons WHERE id=?)",
                String.class, initial.id())).isEqualTo("Published v1");
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


    @Test
    void importsLessonBatchAsDraftsWithProvenanceAndNoPublication() {
        String slug = "bulk-import-" + UUID.randomUUID();
        var item = new LessonRecord("java-language-fundamentals", slug, "Imported lesson", "Imported summary",
                "BEGINNER", new Provenance("EXTERNAL_REFERENCE", "Official Java documentation", "Oracle"),
                new Content(1, List.of(Map.of("type", "markdown", "key", "overview", "body", "Imported body"))),
                "Initial import");
        var result = imports.importLessons(editor, new BatchRequest(1, List.of(item)));
        assertThat(result.importedCount()).isEqualTo(1);
        assertThat(result.lessons().getFirst().status()).isEqualTo("DRAFT");
        UUID lessonId = jdbc.queryForObject("SELECT id FROM lessons WHERE slug=?", UUID.class, slug);
        assertThat(jdbc.queryForObject("SELECT status FROM lessons WHERE id=?", String.class, lessonId)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT published_revision_id IS NULL FROM lessons WHERE id=?", Boolean.class, lessonId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content_review_tasks WHERE lesson_id=?", Integer.class, lessonId)).isZero();
        assertThat(jdbc.queryForObject("SELECT source_description FROM lesson_revisions WHERE lesson_id=?", String.class, lessonId))
                .isEqualTo("Official Java documentation");
        assertThat(jdbc.queryForObject("SELECT attribution FROM lesson_revisions WHERE lesson_id=?", String.class, lessonId))
                .isEqualTo("Oracle");
    }

    @Test
    void rejectsDuplicateKeysInBatchBeforeWritingAnyLesson() {
        String slug = "duplicate-import-" + UUID.randomUUID();
        var item = new LessonRecord("java-language-fundamentals", slug, "Imported lesson", null,
                "BEGINNER", new Provenance("ADMIN_AUTHORED", "JIA editorial team", null),
                new Content(1, List.of(Map.of("type", "markdown", "body", "Body"))), null);
        assertThatThrownBy(() -> imports.importLessons(editor, new BatchRequest(1, List.of(item, item))))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lessons WHERE slug=?", Integer.class, slug)).isZero();
    }

    @Test
    void rejectsExternalImportWithoutAttribution() {
        var item = new LessonRecord("java-language-fundamentals", "missing-attribution-" + UUID.randomUUID(),
                "Imported lesson", null, "BEGINNER",
                new Provenance("EXTERNAL_REFERENCE", "External source", " "),
                new Content(1, List.of(Map.of("type", "markdown", "body", "Body"))), null);
        assertThatThrownBy(() -> imports.importLessons(editor, new BatchRequest(1, List.of(item))))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rollbackCreatesANewRevisionAndNeverOverwritesPublishedContent() {
        String slug = "rollback-" + UUID.randomUUID();
        var initial = service.createDraft(editor, request(slug, "Version one"));
        UUID firstTask = (UUID) service.submitForReview(editor, initial.id()).get("taskId");
        service.decideReview(reviewer, firstTask, new ReviewDecisionRequest("APPROVE", "Approved"));
        service.publish(admin, initial.id());
        UUID originalPublished = jdbc.queryForObject(
                "SELECT published_revision_id FROM lessons WHERE id=?", UUID.class, initial.id());

        var update = service.updateDraft(editor, initial.id(), request(slug, "Version two"));
        UUID secondTask = (UUID) service.submitForReview(editor, initial.id()).get("taskId");
        service.decideReview(reviewer, secondTask, new ReviewDecisionRequest("APPROVE", "Approved v2"));
        service.publish(admin, initial.id());
        assertThat(jdbc.queryForObject(
                "SELECT title FROM lesson_revisions WHERE id=(SELECT published_revision_id FROM lessons WHERE id=?)",
                String.class, initial.id())).isEqualTo("Version two");

        var rolledBack = service.rollback(editor, initial.id(),
                new com.jia.academy.content.api.ContentAuthoringDtos.RollbackRequest(1, "Restore reviewed baseline"));
        assertThat(rolledBack.revisionNumber()).isGreaterThan(update.revisionNumber());
        assertThat(rolledBack.status()).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT published_revision_id FROM lessons WHERE id=?",
                UUID.class, initial.id())).isNotEqualTo(originalPublished);
        assertThat(jdbc.queryForObject(
                "SELECT title FROM lesson_revisions WHERE id=(SELECT published_revision_id FROM lessons WHERE id=?)",
                String.class, initial.id())).isEqualTo("Version two");
        assertThat(jdbc.queryForObject(
                "SELECT title FROM lesson_revisions WHERE id=(SELECT draft_revision_id FROM lessons WHERE id=?)",
                String.class, initial.id())).isEqualTo("Version one");
    }

    private static CurrentUser user(String name, String email, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,name,email,password_hash,status) VALUES (?,?,?,'test-hash','ACTIVE')",
                id, name, email);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code=?", id, role);
        return new CurrentUser(id, name, email, List.of(role), List.of(), List.of(UUID.fromString("50000000-0000-0000-0000-000000000001")));
    }

    private static LessonDraftRequest request(String slug, String title) {
        return new LessonDraftRequest(
                "java-language-fundamentals", slug, title, "A test lesson", "BEGINNER",
                "ADMIN_AUTHORED",
                "{\"schemaVersion\":1,\"blocks\":[{\"type\":\"markdown\",\"key\":\"overview\",\"body\":\"Example\"}]}",
                "Integration test");
    }
}
