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
}
