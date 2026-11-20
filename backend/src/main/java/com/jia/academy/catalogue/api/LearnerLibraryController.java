package com.jia.academy.catalogue.api;

import com.jia.academy.security.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/me")
public class LearnerLibraryController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public LearnerLibraryController(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    @GetMapping("/lesson-progress")
    public List<Map<String,Object>> progress(@AuthenticationPrincipal CurrentUser user) {
        requireUser(user);
        return jdbc.query("""
            SELECT l.slug, p.content_revision, p.status, p.progress_percent,
                   p.last_position_json::text AS last_position_json, p.updated_at
            FROM learner_lesson_progress p JOIN lessons l ON l.id=p.lesson_id
            WHERE p.user_id=? ORDER BY p.updated_at DESC LIMIT 200
            """, (rs,n) -> {
                Map<String,Object> row=new LinkedHashMap<>();
                row.put("lessonSlug",rs.getString("slug"));
                row.put("contentRevision",rs.getInt("content_revision"));
                row.put("status",rs.getString("status"));
                row.put("progressPercent",rs.getBigDecimal("progress_percent"));
                row.put("lastPosition",rs.getString("last_position_json"));
                row.put("updatedAt",rs.getTimestamp("updated_at").toInstant().toString());
                return row;
            }, user.id());
    }

    @PutMapping("/lesson-progress/{lessonSlug}")
    @Transactional
    public Map<String,Object> updateProgress(@AuthenticationPrincipal CurrentUser user,
            @PathVariable String lessonSlug, @RequestBody Map<String,Object> body) {
        requireUser(user);
        Object rawPercent=body.get("progressPercent");
        if (!(rawPercent instanceof Number n) || n.doubleValue()<0 || n.doubleValue()>100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"progressPercent must be between 0 and 100");
        int revision=body.get("contentRevision") instanceof Number n ? n.intValue() : -1;
        if(revision<1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"contentRevision must be positive");
        var lessons=jdbc.query("SELECT id, published_revision_id FROM lessons WHERE slug=? AND status='PUBLISHED'",
            (rs,n)->new Object[]{rs.getObject("id",UUID.class),rs.getObject("published_revision_id",UUID.class)},lessonSlug);
        if(lessons.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Lesson not found");
        UUID lessonId=(UUID)lessons.getFirst()[0];
        Integer currentRevision=jdbc.queryForObject("SELECT revision_number FROM lesson_revisions WHERE id=?",Integer.class,lessons.getFirst()[1]);
        if(currentRevision==null || revision>currentRevision) throw new ResponseStatusException(HttpStatus.CONFLICT,"Unknown lesson revision");
        boolean completed=n.doubleValue()>=100;
        jdbc.update("""
            INSERT INTO learner_lesson_progress(user_id,lesson_id,content_revision,status,progress_percent,last_position_json,completed_at,updated_at)
            VALUES (?,?,?, ?,?,?::jsonb,CASE WHEN ? THEN now() ELSE NULL END,now())
            ON CONFLICT(user_id,lesson_id) DO UPDATE SET
              content_revision=EXCLUDED.content_revision,
              status=CASE WHEN learner_lesson_progress.status='COMPLETED' THEN 'COMPLETED' ELSE EXCLUDED.status END,
              progress_percent=GREATEST(learner_lesson_progress.progress_percent,EXCLUDED.progress_percent),
              last_position_json=COALESCE(EXCLUDED.last_position_json,learner_lesson_progress.last_position_json),
              completed_at=COALESCE(learner_lesson_progress.completed_at,EXCLUDED.completed_at),
              updated_at=now()
            """,user.id(),lessonId,revision,completed?"COMPLETED":"IN_PROGRESS",
            BigDecimal.valueOf(n.doubleValue()),jsonPosition(body.get("lastPosition")),
            completed);
        return Map.of("lessonSlug",lessonSlug,"contentRevision",revision,"status",completed?"COMPLETED":"IN_PROGRESS",
                "progressPercent",n.doubleValue());
    }

    @GetMapping("/bookmarks")
    public List<Map<String,Object>> bookmarks(@AuthenticationPrincipal CurrentUser user) {
        requireUser(user);
        return jdbc.query("""
            SELECT l.slug,l.title,b.created_at FROM learner_bookmarks b
            JOIN lessons l ON l.id=b.lesson_id WHERE b.user_id=? ORDER BY b.created_at DESC LIMIT 200
            """,(rs,n)->Map.of("lessonSlug",rs.getString("slug"),"title",rs.getString("title"),
                "createdAt",rs.getTimestamp("created_at").toInstant().toString()),user.id());
    }

    @PutMapping("/bookmarks/{lessonSlug}")
    @Transactional
    public Map<String,Object> bookmark(@AuthenticationPrincipal CurrentUser user,@PathVariable String lessonSlug) {
        requireUser(user);
        int changed=jdbc.update("""
            INSERT INTO learner_bookmarks(user_id,lesson_id)
            SELECT ?,id FROM lessons WHERE slug=? AND status='PUBLISHED'
            ON CONFLICT(user_id,lesson_id) DO NOTHING
            """,user.id(),lessonSlug);
        if(changed==0 && jdbc.queryForObject("SELECT COUNT(*) FROM lessons WHERE slug=? AND status='PUBLISHED'",Integer.class,lessonSlug)==0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Lesson not found");
        return Map.of("lessonSlug",lessonSlug,"bookmarked",true);
    }

    @DeleteMapping("/bookmarks/{lessonSlug}")
    @Transactional
    public Map<String,Object> removeBookmark(@AuthenticationPrincipal CurrentUser user,@PathVariable String lessonSlug) {
        requireUser(user);
        jdbc.update("""
            DELETE FROM learner_bookmarks b USING lessons l
            WHERE b.lesson_id=l.id AND b.user_id=? AND l.slug=?
            """,user.id(),lessonSlug);
        return Map.of("lessonSlug",lessonSlug,"bookmarked",false);
    }

    private String jsonPosition(Object value) {
        if (value == null) return null;
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"lastPosition must be valid JSON"); }
    }

    private static void requireUser(CurrentUser user) {
        if(user==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required");
    }
}
