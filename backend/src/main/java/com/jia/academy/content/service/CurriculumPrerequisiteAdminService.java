package com.jia.academy.content.service;

import com.jia.academy.security.CurrentUser;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Academy-scoped, transactional curriculum prerequisite administration. */
@Service
public class CurriculumPrerequisiteAdminService {
    private final JdbcTemplate jdbc;
    public CurriculumPrerequisiteAdminService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public List<PrerequisiteItem> get(UUID lessonId, CurrentUser actor) {
        UUID academy = academy(actor);
        requireLesson(lessonId, academy);
        return jdbc.query("""
            SELECT p.prerequisite_lesson_id, l.slug, l.title
            FROM lesson_prerequisites p
            JOIN lessons l ON l.id=p.prerequisite_lesson_id
            JOIN categories c ON c.id=l.category_id
            JOIN technologies t ON t.id=c.technology_id
            WHERE p.lesson_id=? AND t.academy_id=?
            ORDER BY l.title, l.slug
            """, (rs, n) -> new PrerequisiteItem(rs.getObject(1, UUID.class),
                rs.getString("slug"), rs.getString("title")), lessonId, academy);
    }

    @Transactional
    public List<PrerequisiteItem> replace(UUID lessonId, List<UUID> prerequisiteIds, CurrentUser actor) {
        UUID academy = academy(actor);
        requireLesson(lessonId, academy);
        if (prerequisiteIds == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "prerequisiteLessonIds is required");
        Set<UUID> desired = new LinkedHashSet<>(prerequisiteIds);
        if (desired.size() > 250)\n            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A lesson can have at most 250 prerequisites");\n        if (desired.size() != prerequisiteIds.size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prerequisite lesson IDs must be unique");
        if (desired.contains(lessonId))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A lesson cannot be its own prerequisite");
        Set<UUID> academyLessons = new HashSet<>(jdbc.query("""
            SELECT l.id FROM lessons l JOIN categories c ON c.id=l.category_id
            JOIN technologies t ON t.id=c.technology_id WHERE t.academy_id=?
            """, (rs, n) -> rs.getObject(1, UUID.class), academy));
        if (!academyLessons.containsAll(desired))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Every prerequisite must be a lesson in the active academy");

        Map<UUID, Set<UUID>> graph = new HashMap<>();
        academyLessons.forEach(id -> graph.put(id, new HashSet<>()));
        jdbc.query("""
            SELECT p.lesson_id,p.prerequisite_lesson_id FROM lesson_prerequisites p
            JOIN lessons l ON l.id=p.lesson_id JOIN categories c ON c.id=l.category_id
            JOIN technologies t ON t.id=c.technology_id WHERE t.academy_id=?
            """, rs -> graph.get(rs.getObject(1, UUID.class)).add(rs.getObject(2, UUID.class)), academy);
        graph.put(lessonId, new HashSet<>(desired));
        if (hasCycle(graph))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Prerequisite changes would create a curriculum cycle");

        jdbc.update("DELETE FROM lesson_prerequisites WHERE lesson_id=?", lessonId);
        for (UUID prerequisite : desired) {
            jdbc.update("INSERT INTO lesson_prerequisites(lesson_id,prerequisite_lesson_id) VALUES (?,?)",
                lessonId, prerequisite);
        }
        jdbc.update("""
            INSERT INTO audit_events(id,actor_user_id,academy_id,action,entity_type,entity_id,metadata_json,occurred_at)
            VALUES (?,?,?,?,?,?,jsonb_build_object('prerequisiteCount',?),?)
            """, UUID.randomUUID(), actor.id(), academy, "CURRICULUM_PREREQUISITES_UPDATED",
            "LESSON", lessonId, desired.size(), Timestamp.from(Instant.now()));
        return get(lessonId, actor);
    }

    private void requireLesson(UUID id, UUID academy) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM lessons l JOIN categories c ON c.id=l.category_id
            JOIN technologies t ON t.id=c.technology_id WHERE l.id=? AND t.academy_id=?
            """, Integer.class, id, academy);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found");
    }
    private static UUID academy(CurrentUser actor) {
        if (actor.academyIds()==null || actor.academyIds().isEmpty())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "An active academy membership is required");
        return actor.academyIds().getFirst();
    }
    private static boolean hasCycle(Map<UUID, Set<UUID>> graph) {
        Map<UUID,Integer> indegree = new HashMap<>();
        graph.keySet().forEach(id -> indegree.put(id, 0));
        graph.values().forEach(next -> next.forEach(id -> indegree.computeIfPresent(id,(k,v)->v+1)));
        ArrayDeque<UUID> ready = new ArrayDeque<>();
        indegree.forEach((id,degree)->{if(degree==0)ready.add(id);});
        int visited=0;
        while(!ready.isEmpty()) {
            UUID id=ready.remove(); visited++;
            for(UUID next:graph.getOrDefault(id,Set.of())) {
                int degree=indegree.computeIfPresent(next,(k,v)->v-1);
                if(degree==0)ready.add(next);
            }
        }
        return visited != graph.size();
    }
    public record PrerequisiteItem(UUID id, String slug, String title) {}
}