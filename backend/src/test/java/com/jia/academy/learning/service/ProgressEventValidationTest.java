package com.jia.academy.learning.service;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ProgressEventValidationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void academyMembershipMustExistInsteadOfThrowingUnexpectedIndexException() {
        assertThrows(ResponseStatusException.class, () -> ProgressEventService.requireAcademyId(List.of()));
        UUID academy = UUID.randomUUID();
        assertEquals(academy, ProgressEventService.requireAcademyId(List.of(academy)));
    }

    @Test void rejectsSensitiveFieldsRecursivelyAndCaseInsensitively() throws Exception {
        var nested = mapper.readTree("{\\"metadata\\":{\\"Answer_Key\\":\\"secret\\"}}");
        assertThrows(ResponseStatusException.class, () -> ProgressEventService.validatePayload(nested));
        var token = mapper.readTree("{\\"accessToken\\":\\"x\\"}");
        assertThrows(ResponseStatusException.class, () -> ProgressEventService.validatePayload(token));
    }

    @Test void acceptsSmallNonSensitiveEventMetadata() throws Exception {
        var payload = mapper.readTree("{\\"section\\":\\"collections\\",\\"elapsedMs\\":4500}");
        assertDoesNotThrow(() -> ProgressEventService.validatePayload(payload));
    }
}
