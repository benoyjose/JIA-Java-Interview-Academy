package com.jia.academy.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class LessonContentValidator {
    static final int SUPPORTED_SCHEMA_VERSION = 1;
    static final int MAX_BLOCKS = 250;
    static final int MAX_CONTENT_BYTES = 1_048_576;
    static final int MAX_TEXT_FIELD_CHARS = 200_000;
    private final ObjectMapper mapper;

    public LessonContentValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void validate(String contentJson) {
        if (contentJson == null || contentJson.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
            badRequest("Lesson content must not exceed 1 MiB");
        }
        try {
            JsonNode root = mapper.readTree(contentJson);
            if (root == null || !root.isObject()) {
                badRequest("Content must be a JSON object");
            }
            JsonNode schemaVersion = root.path("schemaVersion");
            if (!schemaVersion.isIntegralNumber() || schemaVersion.asInt(-1) != SUPPORTED_SCHEMA_VERSION) {
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
        if (type.isBlank()) {
            badRequest("Content block " + index + " requires a non-empty type");
        }
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
        if (block.path(field).asText().length() > MAX_TEXT_FIELD_CHARS) {
            badRequest("Content block " + index + " field " + field + " exceeds the maximum length");
        }
    }

    private void badRequest(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
