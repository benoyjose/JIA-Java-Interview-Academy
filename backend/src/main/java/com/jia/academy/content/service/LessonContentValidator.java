package com.jia.academy.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class LessonContentValidator {
    static final int SUPPORTED_SCHEMA_VERSION = 1;
    static final int MAX_BLOCKS = 250;
    private final ObjectMapper mapper;

    public LessonContentValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void validate(String contentJson) {
        try {
            JsonNode root = mapper.readTree(contentJson);
            if (root == null || !root.isObject()) {
                badRequest("Content must be a JSON object");
            }
            if (root.path("schemaVersion").asInt(-1) != SUPPORTED_SCHEMA_VERSION) {
                badRequest("Unsupported lesson content schema version");
            }
            JsonNode blocks = root.path("blocks");
            if (!blocks.isArray()) {
                badRequest("Content must include a blocks array");
            }
            if (blocks.size() > MAX_BLOCKS) {
                badRequest("Lesson content cannot contain more than " + MAX_BLOCKS + " blocks");
            }
            for (int i = 0; i < blocks.size(); i++) {
                JsonNode block = blocks.get(i);
                if (!block.isObject()) {
                    badRequest("Content block " + i + " must be an object");
                }
                validateBlock(block, i);
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content JSON is invalid");
        }
    }

    private void validateBlock(JsonNode block, int index) {
        String type = block.path("type").asText("");
        switch (type) {
            case "markdown", "callout" -> requireText(block, "body", index);
            case "code" -> {
                requireText(block, "source", index);
                requireText(block, "language", index);
            }
            case "interviewAnswer" -> {
                JsonNode body = block.path("body");
                if (!body.isArray() || body.isEmpty()) {
                    badRequest("Interview answer block " + index + " must contain at least one point");
                }
                for (JsonNode point : body) {
                    if (!point.isTextual() || point.asText().isBlank()) {
                        badRequest("Interview answer block " + index + " contains an empty point");
                    }
                }
            }
            default -> {
                // Forward-compatible: clients safely skip unknown block types.
            }
        }
    }

    private void requireText(JsonNode block, String field, int index) {
        if (!block.path(field).isTextual() || block.path(field).asText().isBlank()) {
            badRequest("Content block " + index + " requires a non-empty " + field);
        }
    }

    private void badRequest(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
