package com.jia.academy.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jia.academy.analytics.service.AcademyAnalyticsService;
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
class AcademyAnalyticsPostgresIntegrationTest {
    private static final UUID JIA_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18.7-alpine")
                    .withDatabaseName("jia_analytics_test").withUsername("jia").withPassword("jia_test_only");

    private static JdbcTemplate jdbc;
    private static AcademyAnalyticsService analytics;
    private static UUID academyId;
    private static CurrentUser academyAdmin;

    @BeforeAll
    static void migrateAndSeed() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        analytics = new AcademyAnalyticsService(jdbc);

        academyId = UUID.randomUUID();
        jdbc.update("INSERT INTO academies(id,code,name,status) VALUES (?,'ANALYTICS','Analytics Academy','ACTIVE')",
                academyId);
        UUID adminId = createUser("analytics-admin@example.test", "ADMIN");
        jdbc.update("UPDATE academy_memberships SET is_default=FALSE WHERE user_id=? AND academy_id=?",
                adminId, JIA_ID);
        jdbc.update("INSERT INTO academy_memberships(user_id,academy_id,status,is_default) VALUES (?,?,'ACTIVE',TRUE)",
                adminId, academyId);
        academyAdmin = new CurrentUser(adminId, "Analytics Admin", "analytics-admin@example.test",
                List.of("ADMIN"), List.of("users.manage"), List.of(academyId));

        UUID learnerId = createUser("analytics-learner@example.test", "LEARNER");
        jdbc.update("UPDATE academy_memberships SET is_default=FALSE WHERE user_id=? AND academy_id=?",
                learnerId, JIA_ID);
        jdbc.update("INSERT INTO academy_memberships(user_id,academy_id,status,is_default) VALUES (?,?,'ACTIVE',TRUE)",
                learnerId, academyId);

        UUID technologyId = UUID.randomUUID();
        jdbc.update("INSERT INTO technologies(id,academy_id,slug,name,sort_order,status) VALUES (?,?,'analytics-java','Analytics Java',1,'PUBLISHED')",
                technologyId, academyId);
        UUID categoryId = UUID.randomUUID();
        jdbc.update("INSERT INTO categories(id,technology_id,slug,name,sort_order,status) VALUES (?,?,'analytics-basics','Analytics Basics',1,'PUBLISHED')",
                categoryId, technologyId);
        UUID lessonId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO lessons(id,category_id,slug,title,difficulty,source_type,status)
                VALUES (?,?,'analytics-lesson','Analytics Lesson','BEGINNER','ADMIN_AUTHORED','PUBLISHED')
                """, lessonId, categoryId);
        jdbc.update("""
                INSERT INTO learner_lesson_progress(user_id,lesson_id,content_revision,status,progress_percent,completed_at)
                VALUES (?, ?,1,'COMPLETED',100,now())
                """, learnerId, lessonId);
        jdbc.update("""
                INSERT INTO quiz_attempts(id,user_id,academy_id,technology_id,difficulty,mode,status,
                    question_count,correct_count,score_percent,started_at,submitted_at)
                VALUES (?, ?, ?, ?,'BEGINNER','PRACTICE','SUBMITTED',10,8,82.50,now(),now())
                """, UUID.randomUUID(), learnerId, academyId, technologyId);

        // JIA activity is intentionally present to prove the summary does not mix academies.
        UUID jiaLearnerId = createUser("analytics-jia-learner@example.test", "LEARNER");
        jdbc.update("""
                INSERT INTO quiz_attempts(id,user_id,academy_id,difficulty,mode,status,question_count,
                    correct_count,score_percent,started_at,submitted_at)
                VALUES (?, ?, ?,'BEGINNER','PRACTICE','SUBMITTED',10,2,20.00,now(),now())
                """, UUID.randomUUID(), jiaLearnerId, JIA_ID);
    }

    @Test
    void summaryAggregatesOnlyTheAdminsDefaultAcademyAndReturnsNoLearnerPii() {
        var result = analytics.summary(academyAdmin, 30);

        assertThat(result.academyCode()).isEqualTo("ANALYTICS");
        assertThat(result.totalLearners()).isEqualTo(1);
        assertThat(result.activeLearners()).isEqualTo(1);
        assertThat(result.lessonsCompleted()).isEqualTo(1);
        assertThat(result.quizAttempts()).isEqualTo(1);
        assertThat(result.averageScorePercent()).isEqualByComparingTo(new BigDecimal("82.50"));
        assertThat(result.scoredAttempts()).isEqualTo(1);
        assertThat(result.topTopics()).hasSize(1);
        assertThat(result.topTopics().getFirst().topicSlug()).isEqualTo("analytics-java");
        assertThat(result.toString()).doesNotContain("analytics-learner@example.test");
    }

    @Test
    void rejectsOutOfRangeWindowAndNonAdminActors() {
        assertThatThrownBy(() -> analytics.summary(academyAdmin, 0))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(400));
        CurrentUser learner = new CurrentUser(UUID.randomUUID(), "Learner", "learner@example.test",
                List.of("LEARNER"), List.of(), List.of(academyId));
        assertThatThrownBy(() -> analytics.summary(learner, 30))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(403));
    }

    private static UUID createUser(String email, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,name,email,password_hash,status) VALUES (?,? ,?,'test-hash','ACTIVE')",
                id, email, email);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code=?", id, role);
        return id;
    }
    @Test
    void unscoredSubmittedAttemptsIncreaseAttemptCountWithoutChangingScoreDenominator() {
        UUID learnerId = jdbc.queryForObject("SELECT id FROM users WHERE email='analytics-learner@example.test'", UUID.class);
        UUID technologyId = jdbc.queryForObject("SELECT id FROM technologies WHERE slug='analytics-java' AND academy_id=?", UUID.class, academyId);
        jdbc.update("""
                INSERT INTO quiz_attempts(id,user_id,academy_id,technology_id,difficulty,mode,status,question_count,correct_count,score_percent,started_at,submitted_at)
                VALUES (?, ?, ?, ?,'BEGINNER','PRACTICE','SUBMITTED',10,0,NULL,now(),now())
                """, UUID.randomUUID(), learnerId, academyId, technologyId);
        var result = analytics.summary(academyAdmin, 30);
        assertThat(result.quizAttempts()).isEqualTo(2);
        assertThat(result.scoredAttempts()).isEqualTo(1);
        assertThat(result.averageScorePercent()).isEqualByComparingTo(new BigDecimal("82.50"));
    }

}
