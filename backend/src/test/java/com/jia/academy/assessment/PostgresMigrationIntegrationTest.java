package com.jia.academy.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PostgresMigrationIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18.7-alpine")
                    .withDatabaseName("jia_test")
                    .withUsername("jia")
                    .withPassword("jia_test_only");

    @Test
    void allMigrationsBuildTheExpectedJiaSchema() {
        var result = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(21);

        try (var connection = POSTGRES.createConnection(""); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery(
                    "SELECT code FROM academies WHERE id = '50000000-0000-0000-0000-000000000001'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("code")).isEqualTo("JIA");
            }
            try (var rows = statement.executeQuery("""
                    SELECT COUNT(*) AS column_count
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'questions'
                      AND column_name = 'slug'
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("column_count")).isEqualTo(1);
            }
            try (var rows = statement.executeQuery("""
                    SELECT COUNT(*) AS index_count
                    FROM pg_indexes
                    WHERE schemaname = 'public'
                      AND indexname = 'uq_questions_category_slug'
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("index_count")).isEqualTo(1);
            }
            try (var rows = statement.executeQuery("""
                    SELECT COUNT(*) AS table_count
                    FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name IN ('questions', 'question_revisions', 'quiz_attempts',
                                         'quiz_attempt_questions', 'quiz_attempt_answers',
                                         'auth_login_rate_limits', 'content_releases')
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("table_count")).isEqualTo(7);
            }
            try (var rows = statement.executeQuery("""
                    SELECT COUNT(*) AS column_count
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'quiz_attempts'
                      AND column_name IN ('duration_limit_seconds', 'deadline_at')
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("column_count")).isEqualTo(2);
            }
            try (var rows = statement.executeQuery("""
                    SELECT COUNT(*) AS column_count
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'quiz_attempts'
                      AND column_name = 'challenge_date'
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("column_count")).isEqualTo(1);
            }
            try (var rows = statement.executeQuery("""
                    SELECT COUNT(*) AS index_count
                    FROM pg_indexes
                    WHERE schemaname = 'public'
                      AND indexname = 'uq_quiz_attempts_daily_challenge'
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("index_count")).isEqualTo(1);
            }
            try (var rows = statement.executeQuery("""
                    SELECT COUNT(*) AS index_count
                    FROM pg_indexes
                    WHERE schemaname = 'public'
                      AND indexname = 'idx_quiz_attempts_active_deadline'
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("index_count")).isEqualTo(1);
            }
            try (var rows = statement.executeQuery("""
                    SELECT COUNT(*) AS table_count
                    FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name IN ('learner_lesson_progress', 'learner_bookmarks')
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("table_count")).isEqualTo(2);
            }
            try (var rows = statement.executeQuery("""
                    SELECT COUNT(*) AS index_count
                    FROM pg_indexes
                    WHERE schemaname = 'public'
                      AND indexname IN ('idx_lesson_progress_user_updated', 'idx_learner_bookmarks_user_created')
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("index_count")).isEqualTo(2);
            }
            try (var rows = statement.executeQuery(
                    "SELECT COUNT(*) AS role_count FROM roles WHERE code IN ('LEARNER','EDITOR','REVIEWER','ADMIN')")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("role_count")).isEqualTo(4);
            }
            try (var rows = statement.executeQuery("""
                    SELECT COUNT(*) AS index_count
                    FROM pg_indexes
                    WHERE schemaname = 'public'
                      AND indexname IN (
                        'idx_lessons_admin_updated',
                        'idx_lessons_admin_status_updated',
                        'idx_content_review_pending_submitted',
                        'idx_lessons_title_prefix',
                        'idx_lessons_slug_prefix',
                        'idx_categories_slug_prefix',
                        'idx_users_name_prefix'
                      )
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("index_count")).isEqualTo(7);
            }

            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='academy_memberships'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema='public' AND trigger_name='trg_users_default_academy_membership'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM pg_indexes WHERE schemaname='public' AND indexname IN ('idx_academy_memberships_academy_status_user','idx_academy_memberships_user_status','uq_academy_memberships_one_default')")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(2);
            }
        } catch (Exception ex) {
            throw new AssertionError("Unable to verify migrated PostgreSQL schema", ex);
        }
    }
}
