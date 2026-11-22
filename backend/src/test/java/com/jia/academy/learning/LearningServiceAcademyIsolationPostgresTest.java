package com.jia.academy.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jia.academy.learning.service.LearningService;
import com.jia.academy.security.CurrentUser;
import java.math.BigDecimal;
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
class LearningServiceAcademyIsolationPostgresTest {
    private static final UUID JIA_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18.7-alpine")
                    .withDatabaseName("jia_learning_scope_test")
                    .withUsername("jia")
                    .withPassword("jia_test_only");

    private static JdbcTemplate jdbc;
    private static LearningService learning;
    private static CurrentUser learner;
    private static UUID otherAcademyId;
    private static UUID otherLessonId;
    private static String otherLessonSlug;

    @BeforeAll
    static void migrateAndSeed() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        learning = new LearningService(jdbc, new ObjectMapper());

        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users(id,name,email,password_hash,status)
                VALUES (?,'JIA Learner','jia-learner@example.test','test-hash','ACTIVE')
                """, userId);
        learner = new CurrentUser(userId, "JIA Learner", "jia-learner@example.test",
                List.of("LEARNER"), List.of(), List.of(JIA_ID));

        otherAcademyId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO academies(id,code,name,status)
                VALUES (?,'OTHER','Other Academy','ACTIVE')
                """, otherAcademyId);
        UUID technologyId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO technologies(id,academy_id,slug,name,sort_order,status)
                VALUES (?,?,'other-java','Other Java',1,'PUBLISHED')
                """, technologyId, otherAcademyId);
        UUID categoryId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO categories(id,technology_id,slug,name,sort_order,status)
                VALUES (?,?,'other-basics','Other Basics',1,'PUBLISHED')
                """, categoryId, technologyId);

        otherLessonId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        otherLessonSlug = "other-academy-lesson";
        jdbc.update("""
                INSERT INTO lessons(id,category_id,slug,title,summary,difficulty,content_json,
                    content_revision,source_type,status)
                VALUES (?,?,?,'Other academy lesson',NULL,'BEGINNER','{}'::jsonb,1,'ADMIN_AUTHORED','DRAFT')
                """, otherLessonId, categoryId, otherLessonSlug);
        jdbc.update("""
                INSERT INTO lesson_revisions(id,lesson_id,revision_number,title,summary,difficulty,
                    content_json,source_type,changed_by,change_note)
                VALUES (?,?,1,'Other academy lesson',NULL,'BEGINNER','{}'::jsonb,'ADMIN_AUTHORED',?,'test')
                """, revisionId, otherLessonId, userId);
        jdbc.update("""
                UPDATE lessons SET status='PUBLISHED',published_revision_id=? WHERE id=?
                """, revisionId, otherLessonId);
    }

    @Test
    void learnerCannotUpdateProgressOrBookmarkAnotherAcademysLesson() {
        assertNotFound(() -> learning.updateProgress(learner, otherLessonSlug,
                new LearningService.ProgressRequest(1, BigDecimal.valueOf(20), null)));
        assertNotFound(() -> learning.bookmark(learner, otherLessonSlug));
    }

    @Test
    void learnerListsAndDiagnosticsExcludeOtherAcademyRecords() {
        jdbc.update("""
                INSERT INTO learner_lesson_progress(user_id,lesson_id,content_revision,status,
                    progress_percent,started_at,updated_at)
                VALUES (?,?,1,'IN_PROGRESS',20,now(),now())
                """, learner.id(), otherLessonId);
        jdbc.update("""
                INSERT INTO learner_bookmarks(user_id,lesson_id,created_at) VALUES (?,?,now())
                """, learner.id(), otherLessonId);

        assertThat(learning.progress(learner)).isEmpty();
        assertThat(learning.bookmarks(learner)).isEmpty();
        var diagnostics = learning.diagnostics(learner);
        assertThat(diagnostics.progressItems()).isZero();
        assertThat(diagnostics.bookmarkItems()).isZero();

        learning.removeBookmark(learner, otherLessonSlug);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM learner_bookmarks WHERE user_id=? AND lesson_id=?
                """, Integer.class, learner.id(), otherLessonId)).isEqualTo(1);
    }

    @Test
    void progressUpdatesAreIdempotentMonotonicAndCompletionCannotRegress() {
        UUID technologyId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        String slug = "progress-contract-" + UUID.randomUUID();
        jdbc.update("INSERT INTO technologies(id,academy_id,slug,name,sort_order,status) VALUES (?,?,'progress-tech','Progress Tech',2,'PUBLISHED')",
                technologyId, JIA_ID);
        jdbc.update("INSERT INTO categories(id,technology_id,slug,name,sort_order,status) VALUES (?,?,'progress-category','Progress Category',2,'PUBLISHED')",
                categoryId, technologyId);
        jdbc.update("""
                INSERT INTO lessons(id,category_id,slug,title,summary,difficulty,content_json,
                    content_revision,source_type,status)
                VALUES (?,?,?,'Progress contract',NULL,'BEGINNER','{}'::jsonb,1,'ADMIN_AUTHORED','DRAFT')
                """, lessonId, categoryId, slug);
        jdbc.update("""
                INSERT INTO lesson_revisions(id,lesson_id,revision_number,title,summary,difficulty,
                    content_json,source_type,changed_by,change_note)
                VALUES (?,?,1,'Progress contract',NULL,'BEGINNER','{}'::jsonb,'ADMIN_AUTHORED',?,'test')
                """, revisionId, lessonId, learner.id());
        jdbc.update("UPDATE lessons SET status='PUBLISHED',published_revision_id=? WHERE id=?", revisionId, lessonId);

        var partial = learning.updateProgress(learner, slug,
                new LearningService.ProgressRequest(1, BigDecimal.valueOf(45), null));
        var completed = learning.updateProgress(learner, slug,
                new LearningService.ProgressRequest(1, BigDecimal.valueOf(100), null));
        var stale = learning.updateProgress(learner, slug,
                new LearningService.ProgressRequest(1, BigDecimal.valueOf(20), null));

        assertThat(partial.progressPercent()).isEqualByComparingTo("45");
        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(stale.progressPercent()).isEqualByComparingTo("100");
        assertThat(stale.status()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM learner_lesson_progress WHERE user_id=? AND lesson_id=?",
                Integer.class, learner.id(), lessonId)).isEqualTo(1);
    }

    private static void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value())
                        .isEqualTo(404));
    }
}
