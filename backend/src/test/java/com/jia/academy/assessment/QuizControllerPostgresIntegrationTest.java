package com.jia.academy.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jia.academy.assessment.api.QuizController;
import com.jia.academy.catalogue.api.LearnerLibraryController;
import com.jia.academy.security.CurrentUser;
import com.jia.academy.security.TokenAuthenticationFilter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.sql.Timestamp;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class QuizControllerPostgresIntegrationTest {
    private static final UUID USER_ONE = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final UUID USER_TWO = UUID.fromString("60000000-0000-0000-0000-000000000002");
    private static final UUID ACADEMY = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ACADEMY = UUID.fromString("50000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_TECHNOLOGY = UUID.fromString("11000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_CATEGORY = UUID.fromString("21000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_QUESTION = UUID.fromString("71000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_REVISION = UUID.fromString("72000000-0000-0000-0000-000000000001");
    private static final UUID TECHNOLOGY = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CATEGORY = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID QUESTION_ONE = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final UUID QUESTION_TWO = UUID.fromString("70000000-0000-0000-0000-000000000002");
    private static final UUID REVISION_ONE = UUID.fromString("71000000-0000-0000-0000-000000000001");
    private static final UUID REVISION_TWO = UUID.fromString("71000000-0000-0000-0000-000000000002");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18.7-alpine")
                    .withDatabaseName("jia_quiz_test")
                    .withUsername("jia")
                    .withPassword("jia_test_only");

    private static JdbcTemplate jdbc;
    private static QuizController controller;
    private static LearnerLibraryController learnerLibrary;
    private static CurrentUser learnerOne;
    private static CurrentUser learnerTwo;\n    private static CurrentUser learnerOtherAcademy;

    @BeforeAll
    static void migrateAndSeedReferenceData() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        controller = new QuizController(jdbc, new ObjectMapper());
        learnerLibrary = new LearnerLibraryController(jdbc, new ObjectMapper());
        learnerOne = new CurrentUser(USER_ONE, "Learner One", "one@example.test", List.of("LEARNER"), List.of(), List.of(UUID.fromString("50000000-0000-0000-0000-000000000001")));
        learnerTwo = new CurrentUser(USER_TWO, "Learner Two", "two@example.test", List.of("LEARNER"), List.of(), List.of(ACADEMY));
        learnerOtherAcademy = new CurrentUser(USER_ONE, "Learner One", "one@example.test",
                List.of("LEARNER"), List.of(), List.of(OTHER_ACADEMY));

        jdbc.update("INSERT INTO users(id,name,email,password_hash,status) VALUES (?,?,?,?, 'ACTIVE')",
                USER_ONE, "Learner One", "one@example.test", "test-hash");
        jdbc.update("INSERT INTO users(id,name,email,password_hash,status) VALUES (?,?,?,?, 'ACTIVE')",
                USER_TWO, "Learner Two", "two@example.test", "test-hash");

        insertQuestion(QUESTION_ONE, REVISION_ONE, "Question one");
        insertQuestion(QUESTION_TWO, REVISION_TWO, "Question two");
        jdbc.update("INSERT INTO academies(id,code,name,status) VALUES (?,?,?,'ACTIVE')",
                OTHER_ACADEMY, "OTHER", "Other Academy");
        jdbc.update("""
                INSERT INTO technologies(id,academy_id,slug,name,sort_order,status)
                VALUES (?,?,?,'Other Academy Java',1,'PUBLISHED')
                """, OTHER_TECHNOLOGY, OTHER_ACADEMY, "other-java");
        jdbc.update("""
                INSERT INTO categories(id,technology_id,slug,name,sort_order,status)
                VALUES (?,?,?,'Other Academy Basics',1,'PUBLISHED')
                """, OTHER_CATEGORY, OTHER_TECHNOLOGY, "other-basics");
        jdbc.update("""
                INSERT INTO questions(id,academy_id,technology_id,category_id,question_type,difficulty,status,current_revision)
                VALUES (?,?,?,?,'SINGLE_SELECT','BEGINNER','PUBLISHED',1)
                """, OTHER_QUESTION, OTHER_ACADEMY, OTHER_TECHNOLOGY, OTHER_CATEGORY);
        jdbc.update("""
                INSERT INTO question_revisions(id,question_id,revision_number,prompt_json,options_json,answer_json,
                    explanation,source_type)
                VALUES (?,?,1,'{"text":"Other academy question"}'::jsonb,
                    '[{"value":"A","label":"Option A"}]'::jsonb,'{"correctValues":["A"]}'::jsonb,
                    'A is correct','ADMIN_AUTHORED')
                """, OTHER_REVISION, OTHER_QUESTION);
    }

    @BeforeEach
    void clearAttempts() {
        jdbc.update("DELETE FROM quiz_attempts");
    }

    @Test
    void quizSelectionAndAttemptOwnershipAreScopedToLearnersActiveAcademy() {
        Map<String,Object> started = controller.start(learnerOtherAcademy,
                Map.of("difficulty", "BEGINNER", "questionCount", 1));
        UUID otherAcademyAttempt = (UUID) started.get("attemptId");
        assertThat(jdbc.queryForObject("SELECT academy_id FROM quiz_attempts WHERE id=?",
                UUID.class, otherAcademyAttempt)).isEqualTo(OTHER_ACADEMY);
        assertThat(((Map<?,?>) ((List<?>) started.get("questions")).getFirst()).get("questionId"))
                .isEqualTo(OTHER_QUESTION);

        UUID jiaAttempt = startAttempt();
        assertThatThrownBy(() -> controller.submit(learnerOtherAcademy, jiaAttempt, answers(1, 2)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void createsAttemptAndGradesAllAnswersAgainstSnapshottedRevisions() {
        Map<String, Object> started = startResponse();
        UUID attemptId = (UUID) started.get("attemptId");
        assertThat(started.get("questionCount")).isEqualTo(2);
        assertThat(started.get("questions")).asList().hasSize(2);

        Map<String, Object> submitted = controller.submit(learnerOne, attemptId, answers(1, 2));
        assertThat(submitted.get("correctCount")).isEqualTo(2);
        assertThat(submitted.get("scorePercent")).isEqualTo(100.0);
        assertThat(jdbc.queryForObject("SELECT status FROM quiz_attempts WHERE id=?", String.class, attemptId))
                .isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quiz_attempt_answers WHERE attempt_id=?", Integer.class, attemptId))
                .isEqualTo(2);
    }

    @Test
    void rejectsMissingAnswersWithoutPartiallyGradingAttempt() {
        UUID attemptId = startAttempt();
        assertThatThrownBy(() -> controller.submit(learnerOne, attemptId, Map.of("answers", List.of(answer(1)))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(400));
        assertThat(jdbc.queryForObject("SELECT status FROM quiz_attempts WHERE id=?", String.class, attemptId))
                .isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quiz_attempt_answers WHERE attempt_id=?", Integer.class, attemptId))
                .isZero();
    }

    @Test
    void rejectsDuplicateAnswerPositions() {
        UUID attemptId = startAttempt();
        assertThatThrownBy(() -> controller.submit(learnerOne, attemptId,
                Map.of("answers", List.of(answer(1), answer(1), answer(2)))))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quiz_attempt_answers WHERE attempt_id=?", Integer.class, attemptId))
                .isZero();
    }

    @Test
    void startsTimedMockWithServerDeadlineAndPersistsPartialAnswers() {
        Map<String,Object> started = controller.start(learnerOne,
                Map.of("difficulty", "BEGINNER", "questionCount", 2, "mode", "TIMED_MOCK", "durationSeconds", 900));
        UUID attemptId = (UUID) started.get("attemptId");
        assertThat(started.get("durationLimitSeconds")).isEqualTo(900);
        assertThat(started.get("deadlineAt")).isNotNull();

        controller.saveAnswers(learnerOne, attemptId, Map.of("answers", List.of(answer(1))));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quiz_attempt_answers WHERE attempt_id=?", Integer.class, attemptId))
                .isEqualTo(1);

        jdbc.update("UPDATE quiz_attempts SET deadline_at=now() - INTERVAL '1 second' WHERE id=?", attemptId);
        Map<String,Object> result = controller.submit(learnerOne, attemptId, Map.of("answers", List.of()));
        assertThat(result.get("timedOut")).isEqualTo(true);
        assertThat(result.get("correctCount")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM quiz_attempts WHERE id=?", String.class, attemptId))
                .isEqualTo("SUBMITTED");
    }

    @Test
    void dailyChallengeReturnsSameAttemptAndQuestionSnapshotForTheWholeDatabaseDay() {
        Map<String,Object> first = controller.start(learnerOne,
                Map.of("difficulty", "BEGINNER", "questionCount", 2, "mode", "DAILY_CHALLENGE"));
        Map<String,Object> repeated = controller.start(learnerOne,
                Map.of("difficulty", "EXPERT", "questionCount", 1, "mode", "DAILY_CHALLENGE"));

        assertThat(repeated.get("attemptId")).isEqualTo(first.get("attemptId"));
        assertThat(repeated.get("challengeDate")).isEqualTo(first.get("challengeDate"));
        assertThat(repeated.get("questions")).isEqualTo(first.get("questions"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quiz_attempts WHERE user_id=? AND mode='DAILY_CHALLENGE' AND challenge_date=CURRENT_DATE", Integer.class, USER_ONE)).isEqualTo(1);
    }

    @Test
    void weakAreaModeUsesLearnerPerformanceAndCreatesAnAttempt() {
        UUID previousAttempt = startAttempt();
        controller.submit(learnerOne, previousAttempt,
                Map.of("answers", List.of(answer(1), Map.of("position", 2, "response", Map.of("value", "B"), "timeSpentMs", 100))));

        Map<String,Object> weakArea = controller.start(learnerOne,
                Map.of("difficulty", "BEGINNER", "questionCount", 2, "mode", "WEAK_AREA"));

        assertThat(weakArea.get("mode")).isEqualTo("WEAK_AREA");
        assertThat(weakArea.get("questions")).asList().hasSize(2);
        assertThat(jdbc.queryForObject("SELECT mode FROM quiz_attempts WHERE id=?",
                String.class, weakArea.get("attemptId"))).isEqualTo("WEAK_AREA");
    }

    @Test
    void rejectsTimedMockDurationOutsideSupportedRange() {
        assertThatThrownBy(() -> controller.start(learnerOne,
                Map.of("difficulty", "BEGINNER", "questionCount", 2, "mode", "TIMED_MOCK", "durationSeconds", 30)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void preventsAnotherLearnerFromSubmittingAnAttempt() {
        UUID attemptId = startAttempt();
        assertThatThrownBy(() -> controller.submit(learnerTwo, attemptId, answers(1, 2)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(404));
        assertThat(jdbc.queryForObject("SELECT status FROM quiz_attempts WHERE id=?", String.class, attemptId))
                .isEqualTo("IN_PROGRESS");
    }

    @Test
    void authenticatesBearerTokenAndUpdatesLastUsedTimestamp() throws Exception {
        String rawToken = "jia-postgres-auth-integration-token";
        String tokenHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        UUID tokenId = UUID.fromString("72000000-0000-0000-0000-000000000001");
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code='LEARNER' ON CONFLICT DO NOTHING",
                USER_ONE);
        jdbc.update("""
                INSERT INTO api_tokens(id,user_id,token_hash,token_name,expires_at)
                VALUES (?,?,?,?,?)
                """, tokenId, USER_ONE, tokenHash, "integration-test", Timestamp.from(Instant.now().plusSeconds(3600)));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + rawToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        try {
            new TokenAuthenticationFilter(jdbc).doFilter(request, response, chain);
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNotNull();
            assertThat(authentication.getPrincipal()).isInstanceOf(CurrentUser.class);
            assertThat(((CurrentUser) authentication.getPrincipal()).id()).isEqualTo(USER_ONE);
            assertThat(jdbc.queryForObject("SELECT last_used_at IS NOT NULL FROM api_tokens WHERE id=?",
                    Boolean.class, tokenId)).isTrue();
        } finally {
            SecurityContextHolder.clearContext();
            jdbc.update("DELETE FROM api_tokens WHERE id=?", tokenId);
        }
    }

    @Test
    void expiredPersistedBearerTokenIsNotAuthenticatedOrMarkedAsUsed() throws Exception {
        String rawToken = "jia-expired-postgres-auth-integration-token";
        String tokenHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        UUID tokenId = UUID.fromString("72000000-0000-0000-0000-000000000099");
        jdbc.update("""
                INSERT INTO api_tokens(id,user_id,token_hash,token_name,expires_at)
                VALUES (?,?,?,?,?)
                """, tokenId, USER_ONE, tokenHash, "expired-integration-test", Timestamp.from(Instant.now().minusSeconds(60)));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + rawToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        try {
            new TokenAuthenticationFilter(jdbc).doFilter(request, response, chain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(jdbc.queryForObject("SELECT last_used_at IS NULL FROM api_tokens WHERE id=?",
                    Boolean.class, tokenId)).isTrue();
        } finally {
            SecurityContextHolder.clearContext();
            jdbc.update("DELETE FROM api_tokens WHERE id=?", tokenId);
        }
    }

    @Test
    void preventsSubmittingAnAttemptMoreThanOnce() {
        UUID attemptId = startAttempt();
        controller.submit(learnerOne, attemptId, answers(1, 2));
        assertThatThrownBy(() -> controller.submit(learnerOne, attemptId, answers(1, 2)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(409));
    }


    @Test
    void learnerProgressAndBookmarksAreIdempotentAndKeepCompletionMonotonic() {
        UUID lessonId = UUID.fromString("73000000-0000-0000-0000-000000000001");
        UUID lessonRevisionId = UUID.fromString("74000000-0000-0000-0000-000000000001");
        jdbc.update("""
            INSERT INTO lessons(id,category_id,slug,title,difficulty,content_json,content_revision,source_type,status)
            VALUES (?,?,?,'Progress test lesson','BEGINNER','{}'::jsonb,1,'ADMIN_AUTHORED','PUBLISHED')
            """, lessonId, CATEGORY, "progress-test-lesson");
        jdbc.update("""
            INSERT INTO lesson_revisions(id,lesson_id,revision_number,title,difficulty,content_json,source_type)
            VALUES (?,?,1,'Progress test lesson','BEGINNER','{}'::jsonb,'ADMIN_AUTHORED')
            """, lessonRevisionId, lessonId);
        jdbc.update("UPDATE lessons SET published_revision_id=? WHERE id=?", lessonRevisionId, lessonId);

        Map<String,Object> partial = learnerLibrary.updateProgress(learnerOne, "progress-test-lesson",
                Map.of("contentRevision", 1, "progressPercent", 45.0, "lastPosition", Map.of("blockId", "b2")));
        assertThat(partial.get("progressPercent")).isEqualTo(45.0);

        learnerLibrary.updateProgress(learnerOne, "progress-test-lesson",
                Map.of("contentRevision", 1, "progressPercent", 100.0));
        learnerLibrary.updateProgress(learnerOne, "progress-test-lesson",
                Map.of("contentRevision", 1, "progressPercent", 20.0));

        Map<String,Object> progress = learnerLibrary.progress(learnerOne).getFirst();
        assertThat(progress.get("status")).isEqualTo("COMPLETED");
        assertThat(progress.get("progressPercent")).isEqualTo(new java.math.BigDecimal("100.00"));
        assertThat(progress.get("lastPosition")).isEqualTo(Map.of("blockId", "b2"));

        learnerLibrary.bookmark(learnerOne, "progress-test-lesson");
        learnerLibrary.bookmark(learnerOne, "progress-test-lesson");
        assertThat(learnerLibrary.bookmarks(learnerOne)).hasSize(1);
        learnerLibrary.removeBookmark(learnerOne, "progress-test-lesson");
        assertThat(learnerLibrary.bookmarks(learnerOne)).isEmpty();
    }

    private static void insertQuestion(UUID questionId, UUID revisionId, String prompt) {
        jdbc.update("""
                INSERT INTO questions(id,academy_id,technology_id,category_id,question_type,difficulty,status,current_revision)
                VALUES (?,?,?,?, 'SINGLE_SELECT','BEGINNER','PUBLISHED',1)
                """, questionId, ACADEMY, TECHNOLOGY, CATEGORY);
        jdbc.update("""
                INSERT INTO question_revisions(id,question_id,revision_number,prompt_json,options_json,answer_json,
                    explanation,source_type)
                VALUES (?,?,1,?::jsonb,?::jsonb,?::jsonb,?,'ADMIN_AUTHORED')
                """, revisionId, questionId, "{\"text\":\"" + prompt + "\"}",
                "[{\"value\":\"A\",\"label\":\"Option A\"},{\"value\":\"B\",\"label\":\"Option B\"}]",
                "{\"correctValues\":[\"A\"]}", "A is correct");
    }

    private UUID startAttempt() {
        return (UUID) startResponse().get("attemptId");
    }

    private Map<String, Object> startResponse() {
        return controller.start(learnerOne, Map.of("difficulty", "BEGINNER", "questionCount", 2));
    }

    private static Map<String, Object> answer(int position) {
        return Map.of("position", position, "response", Map.of("value", "A"), "timeSpentMs", 100);
    }

    private static Map<String, Object> answers(int... positions) {
        return Map.of("answers", java.util.Arrays.stream(positions).mapToObj(QuizControllerPostgresIntegrationTest::answer).toList());
    }
}
