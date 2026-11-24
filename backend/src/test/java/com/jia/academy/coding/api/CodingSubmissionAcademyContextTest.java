package com.jia.academy.coding.api;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CodingSubmissionAcademyContextTest {
    @Test void requiresRegisteredAcademyMembership() {
        UUID academy = UUID.randomUUID();
        assertEquals(academy, CodingSubmissionController.requireAcademyId(List.of(academy)));
        assertThrows(ResponseStatusException.class, () -> CodingSubmissionController.requireAcademyId(null));
        assertThrows(ResponseStatusException.class, () -> CodingSubmissionController.requireAcademyId(List.of()));
        assertThrows(ResponseStatusException.class, () -> CodingSubmissionController.requireAcademyId(java.util.Arrays.asList((UUID) null)));
    }
}
