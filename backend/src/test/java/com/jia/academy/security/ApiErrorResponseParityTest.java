package com.jia.academy.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ApiErrorResponseParityTest {
    @Test
    void springStatusCodesMatchSharedCrossBackendFixtures() throws Exception {
        Path fixturePath = Path.of("..", "docs", "api", "error-response-parity.json");
        JsonNode fixture = new ObjectMapper().readTree(Files.readString(fixturePath));

        for (JsonNode testCase : fixture.path("cases")) {
            int status = testCase.path("status").asInt();
            String expectedCode = testCase.path("code").asText();

            assertThat(ApiErrorResponse.codeFor(status))
                    .as("HTTP %s should map to %s", status, expectedCode)
                    .isEqualTo(expectedCode);
        }
    }

    @Test
    void unlistedClientErrorUsesGenericHttpErrorCode() {
        assertThat(ApiErrorResponse.codeFor(418)).isEqualTo("HTTP_ERROR");
    }
}
