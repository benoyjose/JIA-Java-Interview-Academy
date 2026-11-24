package com.jia.academy.learning.service;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ProgressEventValidationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void batchSizeIsBoundedAndRejectsEmptyInput() {
        assertThrows(ResponseStatusException.class, () -> ProgressEventService.validateBatchSize(null));
        assertThrows(ResponseStatusException.class, () -> ProgressEventService.validateBatchSize(List.of()));
        var request = new ProgressEventService.EventRequest(UUID.randomUUID(),
                "LESSON_STARTED", java.time.Instant.now(), null, null);
        assertThrows(ResponseStatusException.class, () -> ProgressEventService.validateBatchSize(
                java.util.Collections.nCopies(101, request)));
        assertDoesNotThrow(() -> ProgressEventService.validateBatchSize(List.of(request)));
    }

    @Test void academyMembershipMustExistInsteadOfThrowingUnexpectedIndexException() {
        assertThrows(ResponseStatusException.class, () -> ProgressEventService.requireAcademyId(List.of()));
        UUID academy = UUID.randomUUID();
        assertEquals(academy, ProgressEventService.requireAcademyId(List.of(academy)));
    }

    @Test void rejectsSensitiveFieldsRecursivelyAndCaseInsensitively() throws Exception {
        var nested = mapper.readTree("{\"metadata\":{\"Answer_Key\":\"secret\"}}");
        assertThrows(ResponseStatusException.class, () -> ProgressEventService.validatePayload(nested));
        var token = mapper.readTree("{\"accessToken\":\"x\"}");
        assertThrows(ResponseStatusException.class, () -> ProgressEventService.validatePayload(token));
    }

    @Test void acceptsSmallNonSensitiveEventMetadata() throws Exception {
        var payload = mapper.readTree("{\"section\":\"collections\",\"elapsedMs\":4500}");
        assertDoesNotThrow(() -> ProgressEventService.validatePayload(payload));
    }

    @Test void validatesFieldsRequiredForLearnerProjectionEvents() throws Exception {
        var progress = mapper.readTree("{\"contentRevision\":3,\"progressPercent\":67.5,\"lastPosition\":{\"scrollOffsetPx\":10}}");
        assertDoesNotThrow(() -> ProgressEventService.validateProjectionEvent("LESSON_STARTED", "streams", progress));
        var completed = mapper.readTree("{\"contentRevision\":3,\"progressPercent\":100}");
        assertDoesNotThrow(() -> ProgressEventService.validateProjectionEvent("LESSON_COMPLETED", "streams", completed));
        var bookmark = mapper.readTree("{\"bookmarked\":false}");
        assertDoesNotThrow(() -> ProgressEventService.validateProjectionEvent("BOOKMARK_CHANGED", "streams", bookmark));
        assertThrows(ResponseStatusException.class, () ->
                ProgressEventService.validateProjectionEvent("LESSON_STARTED", null, progress));
        assertThrows(ResponseStatusException.class, () ->
                ProgressEventService.validateProjectionEvent("LESSON_COMPLETED", "streams", progress));
        assertThrows(ResponseStatusException.class, () ->
                ProgressEventService.validateProjectionEvent("BOOKMARK_CHANGED", "streams", mapper.readTree("{\"bookmarked\":\"false\"}")));
    }
    @Test void batchCannotReuseEventIdForDifferentPayload() throws Exception {
        UUID id = UUID.randomUUID();
        var time = java.time.Instant.now();
        var first = new ProgressEventService.EventRequest(id, "LESSON_STARTED", time,
                "streams", mapper.readTree("{\"contentRevision\":1,\"progressPercent\":10}"));
        var conflict = new ProgressEventService.EventRequest(id, "LESSON_STARTED", time,
                "streams", mapper.readTree("{\"contentRevision\":1,\"progressPercent\":90}"));
        assertThrows(ResponseStatusException.class, () ->
                ProgressEventService.validateBatchEventIdentity(List.of(first, conflict)));
    }

    @Test void identicalRepeatedEventInBatchRemainsIdempotent() throws Exception {
        UUID id = UUID.randomUUID();
        var time = java.time.Instant.now();
        var event = new ProgressEventService.EventRequest(id, "QUESTION_ANSWERED", time,
                "streams", mapper.readTree("{\"questionId\":\"q-1\"}"));
        assertDoesNotThrow(() -> ProgressEventService.validateBatchEventIdentity(List.of(event, event)));
    }

    @Test void projectionPositionOrderingIsStableForOutOfOrderAndEqualTimestampEvents() {
        // Contract: projection uses the latest event-time tuple, with UUID as a deterministic tie-break.
        assertTrue(ProgressEventService.isProjectionEventNewer(
                java.time.Instant.parse("2026-11-24T10:00:01Z"), UUID.fromString("00000000-0000-0000-0000-000000000001"),
                java.time.Instant.parse("2026-11-24T10:00:00Z"), UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")));
        assertFalse(ProgressEventService.isProjectionEventNewer(
                java.time.Instant.parse("2026-11-24T10:00:00Z"), UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                java.time.Instant.parse("2026-11-24T10:00:01Z"), UUID.fromString("00000000-0000-0000-0000-000000000001")));
        assertTrue(ProgressEventService.isProjectionEventNewer(
                java.time.Instant.parse("2026-11-24T10:00:00Z"), UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                java.time.Instant.parse("2026-11-24T10:00:00Z"), UUID.fromString("00000000-0000-0000-0000-000000000001")));
    }
}
