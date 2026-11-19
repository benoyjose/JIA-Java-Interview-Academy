package com.jia.academy.content;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jia.academy.content.service.QuestionImportService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class QuestionImportValidationTest {
    private final QuestionImportService imports =
            new QuestionImportService(mock(JdbcTemplate.class), new ObjectMapper());

    @Test
    void rejectsUnsupportedTypeBeforeDatabaseAccess() throws Exception {
        var request = new ObjectMapper().readTree("""
            {"formatVersion":1,"questions":[{
              "categorySlug":"java-language-fundamentals","slug":"invalid-type",
              "questionType":"CODING","difficulty":"BEGINNER",
              "prompt":{"text":"Question?"},"options":[{"value":"A","label":"A"},{"value":"B","label":"B"}],
              "answer":{"correctValues":["A"]},"explanation":"Because.",
              "provenance":{"sourceType":"ADMIN_AUTHORED","sourceDescription":"JIA"}
            }]}
            """);
        assertThatThrownBy(() -> imports.importQuestions(null, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unsupported question type");
    }

    @Test
    void rejectsAnswerValuesThatDoNotExistInOptions() throws Exception {
        var request = new ObjectMapper().readTree("""
            {"formatVersion":1,"questions":[{
              "categorySlug":"java-language-fundamentals","slug":"invalid-answer",
              "questionType":"SINGLE_SELECT","difficulty":"BEGINNER",
              "prompt":{"text":"Question?"},"options":[{"value":"A","label":"A"},{"value":"B","label":"B"}],
              "answer":{"correctValues":["C"]},"explanation":"Because.",
              "provenance":{"sourceType":"ADMIN_AUTHORED","sourceDescription":"JIA"}
            }]}
            """);
        assertThatThrownBy(() -> imports.importQuestions(null, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Correct values");
    }

    @Test
    void rejectsExternalReferenceWithoutAttribution() throws Exception {
        var request = new ObjectMapper().readTree("""
            {"formatVersion":1,"questions":[{
              "categorySlug":"java-language-fundamentals","slug":"missing-attribution",
              "questionType":"SINGLE_SELECT","difficulty":"BEGINNER",
              "prompt":{"text":"Question?"},"options":[{"value":"A","label":"A"},{"value":"B","label":"B"}],
              "answer":{"correctValues":["A"]},"explanation":"Because.",
              "provenance":{"sourceType":"EXTERNAL_REFERENCE","sourceDescription":"External source"}
            }]}
            """);
        assertThatThrownBy(() -> imports.importQuestions(null, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("require attribution");
    }
}
