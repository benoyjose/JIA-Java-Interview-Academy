package com.jia.academy.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jia.academy.content.api.ContentAuthoringDtos.LessonDraftRequest;
import com.jia.academy.content.api.LessonImportDtos.*;
import com.jia.academy.security.CurrentUser;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LessonImportService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final LessonContentValidator contentValidator;
    private final ContentAuthoringService authoring;

    public LessonImportService(JdbcTemplate jdbc, ObjectMapper mapper,
                               LessonContentValidator contentValidator, ContentAuthoringService authoring) {
        this.jdbc=jdbc; this.mapper=mapper; this.contentValidator=contentValidator; this.authoring=authoring;
    }

    @Transactional
    public BatchResponse importLessons(CurrentUser actor, BatchRequest batch) {
        if (batch.formatVersion() == null || batch.formatVersion() != 1)
            badRequest("Unsupported lesson import format version");
        if (batch.lessons() == null || batch.lessons().isEmpty() || batch.lessons().size() > 100)
            badRequest("Import batch must contain between 1 and 100 lessons");

        Set<String> keys = new HashSet<>();
        for (LessonRecord item : batch.lessons()) {
            if (item == null || item.provenance() == null || item.content() == null) badRequest("Lesson record is incomplete");
            String key = item.slug().trim();
            if (!keys.add(key)) badRequest("Duplicate categorySlug/slug in import batch: " + key);
            if (!List.of("BEGINNER","NOVICE","PROFESSIONAL","EXPERT").contains(item.difficulty()))
                badRequest("Unsupported lesson difficulty for " + item.slug());
            String source = item.provenance().sourceType();
            if (!List.of("USER_ORIGINAL","ADMIN_AUTHORED","EXTERNAL_REFERENCE").contains(source))
                badRequest("Unsupported provenance sourceType for " + item.slug());
            if ("EXTERNAL_REFERENCE".equals(source) &&
                    (item.provenance().attribution() == null || item.provenance().attribution().isBlank()))
                badRequest("External references require attribution: " + item.slug());
            if (item.content().schemaVersion() == null || item.content().schemaVersion() != 1)
                badRequest("Unsupported lesson content schema version: " + item.slug());
            try {
                String contentJson = mapper.writeValueAsString(Map.of("schemaVersion",1,"blocks",item.content().blocks()));
                contentValidator.validate(contentJson);
                Integer categoryCount = jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE slug=?",Integer.class,item.categorySlug());
                if (categoryCount == null || categoryCount == 0)
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Category not found: "+item.categorySlug());
                Integer exists = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM lessons WHERE slug=?
                    """,Integer.class,item.slug());
                if (exists != null && exists > 0)
                    throw new ResponseStatusException(HttpStatus.CONFLICT,"Lesson already exists: "+key);
            } catch (ResponseStatusException ex) { throw ex; }
            catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid content for "+item.slug()); }
        }

        List<ImportedLesson> imported = new ArrayList<>();
        for (LessonRecord item : batch.lessons()) {
            try {
                String contentJson = mapper.writeValueAsString(Map.of("schemaVersion",1,"blocks",item.content().blocks()));
                var draft = authoring.createDraft(actor,new LessonDraftRequest(item.categorySlug(),item.slug(),
                    item.title(),item.summary(),item.difficulty(),item.provenance().sourceType(),
                    contentJson,item.changeNote()));
                jdbc.update("""
                    UPDATE lesson_revisions SET source_description=?, attribution=?
                    WHERE lesson_id=? AND revision_number=?
                    """,item.provenance().sourceDescription(),item.provenance().attribution(),draft.id(),draft.revisionNumber());
                imported.add(new ImportedLesson(draft.slug(),draft.status(),draft.revisionNumber()));
            } catch (ResponseStatusException ex) { throw ex; }
            catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Could not import lesson: "+item.slug()); }
        }
        return new BatchResponse(1,imported.size(),List.copyOf(imported));
    }

    private static void badRequest(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);
    }
}
