package com.jia.academy.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jia.academy.auth.service.AuthRateLimitService;
import com.jia.academy.auth.service.AuthService;
import com.jia.academy.auth.api.AuthDtos.RegisterRequest;
import com.jia.academy.content.service.TaxonomyAdminService;
import com.jia.academy.content.service.LessonRevisionHistoryService;
import com.jia.academy.learning.service.LearningService;
import com.jia.academy.learning.service.LearningService.ProgressRequest;
import java.math.BigDecimal;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
    private static final UUID OTHER_ACADEMY = UUID.fromString("50000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_TECHNOLOGY = UUID.fromString("11000000-0000-0000-0000-000000000099");
    private static final UUID OTHER_CATEGORY = UUID.fromString("21000000-0000-0000-0000-000000000099");
    private static final UUID OTHER_LESSON = UUID.fromString("31000000-0000-0000-0000-000000000099");
    private static final UUID OTHER_REVISION = UUID.fromString("41000000-0000-0000-0000-000000000099");
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
    private static AuthService auth;
    private static TaxonomyAdminService taxonomy;
    private static LessonRevisionHistoryService history;
    private static LearningService learning;
    private static CurrentUser editor;
    private static CurrentUser reviewer;
    private static CurrentUser admin;
    private static CurrentUser otherAcademyAdmin;

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
        auth = new AuthService(jdbc, new BCryptPasswordEncoder());
        taxonomy = new TaxonomyAdminService(jdbc);
        history = new LessonRevisionHistoryService(jdbc);
        learning = new LearningService(jdbc, mapper);
        editor = user("Editor", "EDITOR");
        reviewer = user("Reviewer", "REVIEWER");
        admin = user("Admin", "ADMIN");
        jdbc.update("INSERT INTO academies(id,code,name,status) VALUES (?,?,?,'ACTIVE')",
                OTHER_ACADEMY, "OTHER", "Other Academy");
        jdbc.update("INSERT INTO technologies(id,academy_id,slug,name,sort_order,status) VALUES (?,?,?,'Other Tech',1,'PUBLISHED')",
                OTHER_TECHNOLOGY, OTHER_ACADEMY, "other-tech");
        jdbc.update("INSERT INTO categories(id,technology_id,slug,name,sort_order,status) VALUES (?,?,?,'Other Category',1,'PUBLISHED')",
                OTHER_CATEGORY, OTHER_TECHNOLOGY, "other-category");
        otherAcademyAdmin = user("Other Academy Admin", "ADMIN");
        jdbc.update("UPDATE academy_memberships SET is_default=FALSE WHERE user_id=?", otherAcademyAdmin.id());
        jdbc.update("INSERT INTO academy_memberships(user_id,academy_id,status,is_default) VALUES (?,?,'ACTIVE',TRUE)",
                otherAcademyAdmin.id(), OTHER_ACADEMY);
        otherAcademyAdmin = new CurrentUser(otherAcademyAdmin.id(), otherAcademyAdmin.name(),
                otherAcademyAdmin.email(), otherAcademyAdmin.roles(), otherAcademyAdmin.permissions(), List.of(OTHER_ACADEMY));
        jdbc.update("""
                INSERT INTO lessons(id,category_id,slug,title,summary,difficulty,content_json,content_revision,source_type,status)
                VALUES (?,?,?,'Other Academy Lesson','Isolated release test','BEGINNER',
                    '{"schemaVersion":1,"blocks":[{"type":"markdown","key":"overview","body":"Other academy content"}]}'::jsonb,
                    1,'ADMIN_AUTHORED','PUBLISHED')
                """, OTHER_LESSON, OTHER_CATEGORY, "other-academy-release-lesson");
        jdbc.update("""
                INSERT INTO lesson_revisions(id,lesson_id,revision_number,title,summary,difficulty,content_json,source_type,changed_by,change_note)
                VALUES (?,?,1,'Other Academy Lesson','Isolated release test','BEGINNER',
                    '{"schemaVersion":1,"blocks":[{"type":"markdown","key":"overview","body":"Other academy content"}]}'::jsonb,
                    'ADMIN_AUTHORED',?,'Seed other academy release test')
                """, OTHER_REVISION, OTHER_LESSON, otherAcademyAdmin.id());
        jdbc.update("UPDATE lessons SET published_revision_id=? WHERE id=?", OTHER_REVISION, OTHER_LESSON);
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
    void contentReleaseManifestsAndVersionsAreIsolatedByAcademy() {
        releases.create(admin);
        var otherRelease = releases.create(otherAcademyAdmin);
        assertThat(otherRelease.version()).isEqualTo(1);
        assertThat(otherRelease.items()).extracting(item -> item.slug())
                .containsExactly("other-academy-release-lesson");
        assertThat(releases.latest(otherAcademyAdmin).items()).extracting(item -> item.slug())
                .containsExactly("other-academy-release-lesson");
        assertThat(releases.latest().items()).extracting(item -> item.slug())
                .doesNotContain("other-academy-release-lesson");
        assertThat(releases.get(otherRelease.version(), admin).items()).extracting(item -> item.slug())
                .doesNotContain("other-academy-release-lesson");
        assertThat(releases.lesson(otherRelease.version(), "other-academy-release-lesson", otherAcademyAdmin)
                .title()).isEqualTo("Other Academy Lesson");
        assertThatThrownBy(() -> releases.lesson(otherRelease.version(),
                "other-academy-release-lesson", admin)).isInstanceOf(ResponseStatusException.class);
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

    @Test
    void taxonomyCrudProtectsContentAndArchivesEmptyCategories() {
        String slug = "test-taxonomy-" + UUID.randomUUID().toString().substring(0, 8);
        var created = taxonomy.create(editor, new TaxonomyAdminService.CategoryRequest(
                "core-java", null, slug, "Test taxonomy", 99));
        assertThat(created.status()).isEqualTo("PUBLISHED");
        assertThat(taxonomy.categories("core-java", editor)).extracting(TaxonomyAdminService.CategoryItem::slug)
                .contains(slug);
        var updated = taxonomy.update(editor, created.id(), new TaxonomyAdminService.CategoryRequest(
                "core-java", null, slug + "-updated", "Updated taxonomy", 100));
        assertThat(updated.name()).isEqualTo("Updated taxonomy");
        taxonomy.archive(editor, created.id());
        assertThat(taxonomy.categories("core-java", editor).stream()
                .filter(item -> item.id().equals(created.id())).findFirst().orElseThrow().status())
                .isEqualTo("ARCHIVED");

        String technologySlug = "test-tech-" + UUID.randomUUID().toString().substring(0, 8);
        var technology = taxonomy.createTechnology(editor, new TaxonomyAdminService.TechnologyRequest(
                technologySlug, "Test Technology", "Created by integration test", 999));
        assertThat(technology.status()).isEqualTo("PUBLISHED");
        taxonomy.archiveTechnology(editor, technology.id());
        assertThat(taxonomy.technologies(admin).stream().filter(item -> item.id().equals(technology.id()))
                .findFirst().orElseThrow().status()).isEqualTo("ARCHIVED");
    }

    @Test
    void revisionHistoryIncludesReviewDecisionAndPublishedRevisionMarker() {
        String slug = "revision-history-" + UUID.randomUUID();
        var draft = authoring.createDraft(editor, request(slug, "History lesson"));
        UUID task = (UUID) authoring.submitForReview(editor, draft.id()).get("taskId");
        var beforeDecision = history.history(editor, draft.id());
        assertThat(beforeDecision).hasSize(1);
        assertThat(beforeDecision.getFirst().reviewStatus()).isEqualTo("PENDING");
        authoring.decideReview(reviewer, task, new ReviewDecisionRequest("APPROVE", "History approved"));
        authoring.publish(admin, draft.id());
        assertThat(history.history(editor, draft.id()).getFirst().published()).isTrue();
    }

    @Test
    void learnerProgressAndBookmarksAreMonotonicAndIdempotent() {
        CurrentUser learner = user("Learner", "LEARNER");
        String slug = "offline-sync-" + UUID.randomUUID();
        var draft = authoring.createDraft(editor, request(slug, "Offline sync lesson"));
        UUID task = (UUID) authoring.submitForReview(editor, draft.id()).get("taskId");
        authoring.decideReview(reviewer, task, new ReviewDecisionRequest("APPROVE", "Reviewed"));
        authoring.publish(admin, draft.id());

        var first = learning.updateProgress(learner, slug, new ProgressRequest(1, BigDecimal.valueOf(60), null));
        var stale = learning.updateProgress(learner, slug, new ProgressRequest(1, BigDecimal.valueOf(25), null));
        assertThat(first.progressPercent()).isEqualByComparingTo("60");
        assertThat(stale.progressPercent()).isEqualByComparingTo("60");
        var complete = learning.updateProgress(learner, slug, new ProgressRequest(1, BigDecimal.valueOf(100), null));
        assertThat(complete.status()).isEqualTo("COMPLETED");

        learning.bookmark(learner, slug);
        learning.bookmark(learner, slug);
        assertThat(learning.bookmarks(learner)).extracting(LearningService.BookmarkItem::lessonSlug).contains(slug);
        learning.removeBookmark(learner, slug);
        assertThat(learning.bookmarks(learner)).extracting(LearningService.BookmarkItem::lessonSlug).doesNotContain(slug);
    }

    @Test
    void refreshTokensRotateOnceAndReuseRevokesTheEntireSessionFamily() {
        var registered = auth.register(new RegisterRequest("Refresh Test",
                "refresh-" + UUID.randomUUID() + "@example.test", "long-secure-test-password"));
        var rotated = auth.refresh(registered.refreshToken());
        assertThat(rotated.refreshToken()).isNotEqualTo(registered.refreshToken());
        assertThatThrownBy(() -> auth.refresh(registered.refreshToken()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("reuse detected");
        assertThatThrownBy(() -> auth.refresh(rotated.refreshToken()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void accessTokensAreShortLivedAndFederatedIdentityCannotRemoveLastSignInMethod() {
        var registered = auth.register(new RegisterRequest("Short Token Test",
                "short-token-" + UUID.randomUUID() + "@example.test", "long-secure-test-password"));
        Boolean shortLived = jdbc.queryForObject("""
                SELECT EXISTS(
                    SELECT 1 FROM api_tokens
                    WHERE user_id=? AND expires_at > now()
                      AND expires_at <= now() + interval '16 minutes'
                )
                """, Boolean.class, registered.user().id());
        assertThat(shortLived).isTrue();

        CurrentUser federatedOnly = user("Federated Only", "LEARNER");
        jdbc.update("UPDATE users SET password_hash=NULL WHERE id=?", federatedOnly.id());
        auth.linkFederatedIdentity(federatedOnly, "GOOGLE", "google-" + UUID.randomUUID(),
                "federated@example.test", true);
        assertThatThrownBy(() -> auth.unlinkFederatedIdentity(federatedOnly, "GOOGLE"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Add another sign-in method");

        auth.linkFederatedIdentity(federatedOnly, "APPLE", "apple-" + UUID.randomUUID(),
                "federated-apple@example.test", true);
        auth.unlinkFederatedIdentity(federatedOnly, "GOOGLE");
        Integer remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM auth_identities WHERE user_id=?",
                Integer.class, federatedOnly.id());
        assertThat(remaining).isEqualTo(1);
    }

    private static CurrentUser user(String name, String role) {
        UUID id = UUID.randomUUID();
        String email = role.toLowerCase() + "-" + id + "@example.test";
        jdbc.update("INSERT INTO users(id,name,email,password_hash,status) VALUES (?,?,?,'test-hash','ACTIVE')",
                id, name, email);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code=?", id, role);
        return new CurrentUser(id, name, email, List.of(role), List.of(), List.of(UUID.fromString("50000000-0000-0000-0000-000000000001")));
    }

    private static LessonDraftRequest request(String slug, String title) {
        return new LessonDraftRequest(
                "java-language-fundamentals", slug, title, "Release snapshot test", "BEGINNER",
                "ADMIN_AUTHORED",
                "{\"schemaVersion\":1,\"blocks\":[{\"type\":\"markdown\",\"key\":\"overview\",\"body\":\"Example\"}]}",
                "Integration test");
    }
}
