package com.jia.academy.privacy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jia.academy.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class LearnerPrivacyExportServiceTest {
    private JdbcTemplate jdbc;
    private LearnerPrivacyExportService service;
    private UUID learnerId;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(learnerId = UUID.randomUUID())))
                .thenReturn(List.of());
        service = new LearnerPrivacyExportService(jdbc);
    }

    @Test
    void exportsVersionedLearnerDataAndScopesEveryCollectionToPrincipal() {
        CurrentUser learner = new CurrentUser(learnerId, "Learner", "learner@example.test",
                List.of("LEARNER"), List.of(), List.of(UUID.randomUUID()));

        var archive = service.export(learner);

        assertThat(archive).containsKeys("formatVersion", "generatedAt", "profile",
                "lessonProgress", "bookmarks", "quizAttempts", "quizResponses",
                "revisionSchedule", "notificationPreferences", "notifications");
        assertThat(archive.get("formatVersion")).isEqualTo(1);
        assertThat(archive.get("generatedAt")).isInstanceOf(String.class);
        assertThat(archive).doesNotContainKeys("password", "passwordHash", "accessToken",
                "refreshToken", "providerSecret", "answerKey", "hiddenTests");
        // Eight learner-owned collections are fetched; each SQL call receives only the
        // authenticated principal's UUID, never a client-supplied identifier.
        verify(jdbc, times(8)).queryForList(anyString(), eq(learnerId));
    }
}
