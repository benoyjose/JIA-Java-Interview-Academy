package com.jia.academy.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jia.academy.security.CurrentUser;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuestionImportService {
    private static final Set<String> TYPES = Set.of("SINGLE_SELECT", "MULTI_SELECT", "TRUE_FALSE");
    private static final Set<String> DIFFICULTIES = Set.of("BEGINNER", "NOVICE", "PROFESSIONAL", "EXPERT");
    private static final Set<String> SOURCES = Set.of("USER_ORIGINAL", "ADMIN_AUTHORED", "EXTERNAL_REFERENCE");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public QuestionImportService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public Map<String, Object> importQuestions(CurrentUser actor, JsonNode batch) {
        requireObject(batch, Set.of("formatVersion", "questions"));
        if (!batch.path("formatVersion").isIntegralNumber() || batch.path("formatVersion").intValue() != 1)
            bad("Unsupported question import format version");
        JsonNode records = batch.path("questions");
        if (!records.isArray() || records.isEmpty() || records.size() > 100) bad("Import batch must contain 1 to 100 questions");

        Set<String> keys = new HashSet<>();
        List<UUID> categoryIds = new ArrayList<>();
        for (JsonNode item : records) {
            validate(item);
            String categorySlug = item.path("categorySlug").asText().trim();
            String slug = item.path("slug").asText();
            String key = categorySlug + "/" + slug;
            if (!keys.add(key)) bad("Duplicate categorySlug/slug in import batch: " + key);
            List<UUID> categories = jdbc.query("""
                SELECT c.id FROM categories c WHERE c.slug=?
                """, (rs, row) -> rs.getObject(1, UUID.class), categorySlug);
            if (categories.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found: " + categorySlug);
            UUID categoryId = categories.getFirst();
            categoryIds.add(categoryId);
            if (Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM questions WHERE category_id=? AND slug=?)",
                    Boolean.class, categoryId, slug)))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Question already exists: " + key);
        }

        List<Map<String, Object>> imported = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            JsonNode item = records.get(i);
            UUID questionId = UUID.randomUUID();
            UUID revisionId = UUID.randomUUID();
            UUID categoryId = categoryIds.get(i);
            UUID technologyId = jdbc.queryForObject("SELECT technology_id FROM categories WHERE id=?", UUID.class, categoryId);
            UUID academyId = jdbc.queryForObject("SELECT t.academy_id FROM technologies t WHERE t.id=?", UUID.class, technologyId);
            String source = item.path("provenance").path("sourceType").asText();
            try {
                jdbc.update("""
                    INSERT INTO questions(id,academy_id,technology_id,category_id,slug,question_type,difficulty,status,current_revision)
                    VALUES (?,?,?,?,?,?,?,'DRAFT',1)
                    """, questionId, academyId, technologyId, categoryId, item.path("slug").asText(),
                    item.path("questionType").asText(), item.path("difficulty").asText());
                jdbc.update("""
                    INSERT INTO question_revisions(id,question_id,revision_number,prompt_json,options_json,answer_json,
                        explanation,hint,source_type,source_description,attribution,created_by)
                    VALUES (?,?,1,?::jsonb,?::jsonb,?::jsonb,?,?,?,?,?,?)
                    """, revisionId, questionId, mapper.writeValueAsString(item.path("prompt")),
                    mapper.writeValueAsString(item.path("options")), mapper.writeValueAsString(item.path("answer")),
                    item.path("explanation").asText(), nullableText(item, "hint"), source,
                    item.path("provenance").path("sourceDescription").asText(),
                    nullableText(item.path("provenance"), "attribution"), actor.id());
                jdbc.update("""
                    INSERT INTO audit_events(id,actor_user_id,academy_id,action,entity_type,entity_id,metadata_json)
                    VALUES (?,?,?,'QUESTION_DRAFT_IMPORTED','QUESTION',?,?::jsonb)
                    """, UUID.randomUUID(), actor.id(), academyId, questionId,
                    mapper.writeValueAsString(Map.of("revision", 1, "sourceType", source,
                            "changeNote", Objects.toString(nullableText(item, "changeNote"), ""))));
            } catch (DuplicateKeyException ex) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Question already exists: " + item.path("slug").asText());
            } catch (ResponseStatusException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not import question: " + item.path("slug").asText());
            }
            imported.add(Map.of("categorySlug", item.path("categorySlug").asText(),
                    "slug", item.path("slug").asText(), "status", "DRAFT", "revisionNumber", 1));
        }
        return Map.of("formatVersion", 1, "importedCount", imported.size(), "questions", imported);
    }

    private void validate(JsonNode q) {
        requireObject(q, Set.of("categorySlug","slug","questionType","difficulty","prompt","options","answer","explanation","hint","provenance","changeNote"));
        text(q, "categorySlug", 100); text(q, "slug", 120); text(q, "explanation", 10000);
        if (!q.path("slug").asText().matches("^[a-z0-9]+(?:-[a-z0-9]+)*$")) bad("Invalid question slug");
        if (!TYPES.contains(q.path("questionType").asText()) || !DIFFICULTIES.contains(q.path("difficulty").asText())) bad("Unsupported question type or difficulty");
        JsonNode prompt = q.path("prompt");
        requireObject(prompt, Set.of("text","code")); text(prompt, "text", 10000);
        if (prompt.has("code") && !prompt.get("code").isNull()) {
            requireObject(prompt.get("code"), Set.of("language","source"));
            text(prompt.get("code"), "language", 40); text(prompt.get("code"), "source", 20000);
        }
        JsonNode options = q.path("options");
        if (!options.isArray() || options.size() < 2 || options.size() > 20) bad("Options must contain 2 to 20 entries");
        Set<String> values = new HashSet<>();
        for (JsonNode option : options) {
            requireObject(option, Set.of("value","label")); text(option, "value", 100); text(option, "label", 1000);
            if (!values.add(option.path("value").asText())) bad("Option values must be unique");
        }
        JsonNode answer = q.path("answer");
        requireObject(answer, Set.of("correctValues"));
        JsonNode correct = answer.path("correctValues");
        if (!correct.isArray() || correct.isEmpty() || correct.size() > 20) bad("At least one correct value is required");
        Set<String> correctValues = new HashSet<>();
        for (JsonNode value : correct) {
            if (!value.isTextual() || value.asText().isBlank() || !values.contains(value.asText()) || !correctValues.add(value.asText()))
                bad("Correct values must be unique values present in options");
        }
        String type = q.path("questionType").asText();
        if ("SINGLE_SELECT".equals(type) && correct.size() != 1) bad("SINGLE_SELECT requires exactly one correct value");
        if ("TRUE_FALSE".equals(type) && (!values.equals(Set.of("true","false")) || correct.size() != 1))
            bad("TRUE_FALSE requires exactly true/false options and one correct value");
        text(q, "explanation", 10000);
        if (q.has("hint") && !q.get("hint").isNull() && (!q.get("hint").isTextual() || q.get("hint").asText().length() > 2000)) bad("Invalid hint");
        if (q.has("changeNote") && !q.get("changeNote").isNull() && (!q.get("changeNote").isTextual() || q.get("changeNote").asText().length() > 500)) bad("Invalid changeNote");
        JsonNode provenance = q.path("provenance");
        requireObject(provenance, Set.of("sourceType","sourceDescription","attribution"));
        text(provenance, "sourceType", 30); text(provenance, "sourceDescription", 500);
        String source = provenance.path("sourceType").asText();
        if (!SOURCES.contains(source)) bad("Unsupported provenance sourceType");
        if (provenance.has("attribution") && !provenance.get("attribution").isNull()
                && (!provenance.get("attribution").isTextual() || provenance.get("attribution").asText().length() > 1000)) bad("Invalid attribution");
        if ("EXTERNAL_REFERENCE".equals(source) && (!provenance.path("attribution").isTextual() || provenance.path("attribution").asText().isBlank()))
            bad("External references require attribution");
    }

    private static void requireObject(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject()) bad("Invalid question import object");
        node.fieldNames().forEachRemaining(name -> { if (!allowed.contains(name)) bad("Unknown field: " + name); });
    }
    private static void text(JsonNode node, String field, int max) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > max) bad("Invalid " + field);
    }
    private static String nullableText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
