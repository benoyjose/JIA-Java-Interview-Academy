package com.jia.academy.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiErrorResponseTest {
    @Test
    void buildsRequiredErrorEnvelopeWithoutOptionalFieldsWhenAbsent() {
        Map<String, Object> body = ApiErrorResponse.body("NOT_FOUND", "The requested resource was not found.");

        assertThat(body).containsEntry("code", "NOT_FOUND");
        assertThat(body).containsEntry("message", "The requested resource was not found.");
        assertThat(body).doesNotContainKey("errors");
    }

    @Test
    void includesFieldErrorsWhenProvided() {
        Map<String, Object> body = ApiErrorResponse.body(
                "VALIDATION_ERROR",
                "The request contains invalid fields.",
                Map.of("answers.0.timeSpentMs", List.of("Must be a non-negative integer.")));

        assertThat(body).containsKey("errors");
        assertThat((Map<?, ?>) body.get("errors"))
                .containsEntry("answers.0.timeSpentMs", List.of("Must be a non-negative integer."));
    }

    @Test
    void mapsKnownHttpStatusesToStableCodes() {
        assertThat(ApiErrorResponse.codeFor(400)).isEqualTo("BAD_REQUEST");
        assertThat(ApiErrorResponse.codeFor(401)).isEqualTo("UNAUTHENTICATED");
        assertThat(ApiErrorResponse.codeFor(403)).isEqualTo("FORBIDDEN");
        assertThat(ApiErrorResponse.codeFor(404)).isEqualTo("NOT_FOUND");
        assertThat(ApiErrorResponse.codeFor(409)).isEqualTo("CONFLICT");
        assertThat(ApiErrorResponse.codeFor(422)).isEqualTo("VALIDATION_ERROR");
        assertThat(ApiErrorResponse.codeFor(429)).isEqualTo("RATE_LIMITED");
        assertThat(ApiErrorResponse.codeFor(500)).isEqualTo("INTERNAL_ERROR");
    }
}
