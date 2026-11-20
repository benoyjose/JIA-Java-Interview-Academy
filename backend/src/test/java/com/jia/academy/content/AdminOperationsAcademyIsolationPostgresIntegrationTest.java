package com.jia.academy.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jia.academy.content.service.AdminOperationsService;
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
class AdminOperationsAcademyIsolationPostgresIntegrationTest {
    private static final UUID JIA_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18.7-alpine")
                    .withDatabaseName("jia_admin_scope_test")
                    .withUsername("jia")
                    .withPassword("jia_test_only");

    private static JdbcTemplate jdbc;
    private static AdminOperationsService operations;
    private static UUID secondAcademyId;
    private static UUID jiaQuestionId;
    private static UUID secondAcademyQuestionId;
    private static CurrentUser jiaEditor;
    private static CurrentUser secondAcademyEditor;

    @BeforeAll
    static void migrateAndSeed() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        operations = new AdminOperationsService(jdbc);

        secondAcademyId = UUID.randomUUID();
        jdbc.update("INSERT INTO academies(id,code,name,status) VALUES (?,'ADMINSCOPE','Admin Scope Academy','ACTIVE')",
                secondAcademyId);

        UUID secondTechnologyId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO technologies(id,academy_id,slug,name,sort_order,status)
                VALUES (?,?,'admin-scope-java','Admin Scope Java',1,'PUBLISHED')
                """, secondTechnologyId, secondAcademyId);
        UUID secondCategoryId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO categories(id,technology_id,slug,name,sort_order,status)
                VALUES (?,?,'admin-scope-basics','Admin Scope Basics',1,'PUBLISHED')
                """, secondCategoryId, secondTechnologyId);

        UUID jiaUserId = createUser("admin-jia@example.test");
        UUID secondUserId = createUser("admin-second@example.test");
        jdbc.update("""
                UPDATE academy_memberships SET is_default=FALSE,status='SUSPENDED'
                WHERE user_id=? AND academy_id=?
                """, secondUserId, JIA_ID);
        jdbc.update("""
                INSERT INTO academy_memberships(user_id,academy_id,status,is_default)
                VALUES (?,?,'ACTIVE',TRUE)
                """, secondUserId, secondAcademyId);

        jiaEditor = new CurrentUser(jiaUserId, "JIA Admin", "admin-jia@example.test",
                List.of("EDITOR"), List.of("content.create", "content.review", "content.publish"), List.of(JIA_ID));
        secondAcademyEditor = new CurrentUser(secondUserId, "Second Admin", "admin-second@example.test",
                List.of("EDITOR"), List.of("content.create", "content.review", "content.publish"), List.of(secondAcademyId));

        jiaQuestionId = insertQuestion(JIA_ID,
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                "jia-admin-scope-question", "JIA only question");
        secondAcademyQuestionId = insertQuestion(secondAcademyId, secondTechnologyId, secondCategoryId,
                "second-admin-scope-question", "Second academy only question");
    }

    @Test
    void questionListAndStatusTransitionsAreRestrictedToActorsDefaultAcademy() {
        var secondAcademyQuestions = operations.questions(secondAcademyEditor, null, null, null, 0, 25);
        assertThat(secondAcademyQuestions.items()).extracting(item -> item.id())
                .containsExactly(secondAcademyQuestionId);
        assertThat(secondAcademyQuestions.totalItems()).isEqualTo(1);

        var jiaQuestions = operations.questions(jiaEditor, null, null, null, 0, 25);
        assertThat(jiaQuestions.items()).extracting(item -> item.id()).containsExactly(jiaQuestionId);

        assertThatThrownBy(() -> operations.transitionQuestion(secondAcademyEditor, jiaQuestionId, "IN_REVIEW"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(404));

        var transitioned = operations.transitionQuestion(secondAcademyEditor, secondAcademyQuestionId, "IN_REVIEW");
        assertThat(transitioned.status()).isEqualTo("IN_REVIEW");

        UUID auditAcademyId = jdbc.queryForObject("""
                SELECT academy_id FROM audit_events
                WHERE action='QUESTION_STATUS_CHANGED' AND entity_id=?
                ORDER BY occurred_at DESC LIMIT 1
                """, UUID.class, secondAcademyQuestionId);
        assertThat(auditAcademyId).isEqualTo(secondAcademyId);
    }

    private static UUID createUser(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,name,email,password_hash,status) VALUES (?,? ,?,'test-hash','ACTIVE')",
                id, email, email);
        return id;
    }

    private static UUID insertQuestion(UUID academyId, UUID technologyId, UUID categoryId,
                                       String slug, String prompt) {
        UUID questionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO questions(id,academy_id,technology_id,category_id,slug,question_type,difficulty,status,current_revision)
                VALUES (?,?,?,?,?,'SINGLE_SELECT','BEGINNER','DRAFT',1)
                """, questionId, academyId, technologyId, categoryId, slug);
        jdbc.update("""
                INSERT INTO question_revisions(id,question_id,revision_number,prompt_json,answer_json,explanation,source_type)
                VALUES (?, ?,1,?::jsonb,'{"answer":"A"}'::jsonb,'Integration test explanation','ADMIN_AUTHORED')
                """, UUID.randomUUID(), questionId, "{\"text\":\"" + prompt + "\"}");
        return questionId;
    }
}
