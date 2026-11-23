package com.jia.academy.content.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class LessonContentValidatorTest {
    private final LessonContentValidator validator = new LessonContentValidator(new ObjectMapper());

    @Test
    void acceptsEverySupportedBlockType() {
        assertDoesNotThrow(() -> validator.validate("""
            {"schemaVersion":1,"blocks":[
              {"type":"markdown","body":"Overview"},
              {"type":"code","language":"java","source":"class Demo {}"},
              {"type":"callout","body":"Remember this"},
              {"type":"interviewAnswer","body":["First point","Second point"]}
            ]}
            """));
    }

    @Test
    void acceptsUnknownBlocksForForwardCompatibility() {
        assertDoesNotThrow(() -> validator.validate(
            "{\"schemaVersion\":1,\"blocks\":[{\"type\":\"futureBlock\",\"payload\":true}]}"));
    }

    @Test
    void rejectsMissingOrNonNumericSchemaVersionAndMissingBlocks() {
        assertThrows(ResponseStatusException.class, () -> validator.validate("{\"blocks\":[]}"));
        assertThrows(ResponseStatusException.class, () -> validator.validate("{\"schemaVersion\":\"1\",\"blocks\":[]}"));
        assertThrows(ResponseStatusException.class, () -> validator.validate("{\"schemaVersion\":1}"));
    }

    @Test
    void rejectsUnsupportedSchemaVersion() {
        assertThrows(ResponseStatusException.class, () -> validator.validate(
            "{\"schemaVersion\":2,\"blocks\":[]}"));
    }

    @Test
    void rejectsMissingRequiredBlockFields() {
        assertThrows(ResponseStatusException.class, () -> validator.validate(
            "{\"schemaVersion\":1,\"blocks\":[{\"type\":\"code\",\"language\":\"java\"}]}"));
    }

    @Test
    void rejectsNonObjectBlocks() {
        assertThrows(ResponseStatusException.class, () -> validator.validate(
            "{\"schemaVersion\":1,\"blocks\":[\"not-an-object\"]}"));
    }

    @Test
    void rejectsTooManyBlocks() {
        String blocks = "[" + String.join(",", java.util.Collections.nCopies(251,
            "{\"type\":\"markdown\",\"body\":\"x\"}")) + "]";
        assertThrows(ResponseStatusException.class, () -> validator.validate(
            "{\"schemaVersion\":1,\"blocks\":" + blocks + "}"));
    }

    @Test
    void rejectsBlocksWithoutType() {
        assertThrows(ResponseStatusException.class, () -> validator.validate(
            "{\"schemaVersion\":1,\"blocks\":[{\"body\":\"missing type\"}]}"));
    }

    @Test
    void rejectsContentLargerThanOneMiB() {
        String oversized = "x".repeat(LessonContentValidator.MAX_CONTENT_BYTES + 1);
        assertThrows(ResponseStatusException.class, () -> validator.validate(oversized));
    }

    @Test
    void rejectsOversizedTextFields() {
        String body = "x".repeat(LessonContentValidator.MAX_TEXT_FIELD_CHARS + 1);
        String json = "{\"schemaVersion\":1,\"blocks\":[{\"type\":\"markdown\",\"body\":\""
            + body + "\"}]}";
        assertThrows(ResponseStatusException.class, () -> validator.validate(json));
    }

}
