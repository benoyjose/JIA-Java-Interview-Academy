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
        assertThat(result.migrationsExecuted).isEqualTo(11);

        try (var connection = POSTGRES.createConnection(""); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery(
                    "SELECT code FROM academies WHERE id = '50000000-0000-0000-0000-000000000001'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("code")).isEqualTo("JIA");
            }
            try (var rows = statement.executeQuery("""
                    SELECT COUNT(*) AS table_count
                    FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name IN ('questions', 'question_revisions', 'quiz_attempts',
                                         'quiz_attempt_questions', 'quiz_attempt_answers')
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("table_count")).isEqualTo(5);
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
        } catch (Exception ex) {
            throw new AssertionError("Unable to verify migrated PostgreSQL schema", ex);
        }
    }
}
