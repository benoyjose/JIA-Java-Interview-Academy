package com.jia.academy.discovery.api;

import com.jia.academy.security.CurrentUser;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/me/academies/{academyCode}")
public class LearnerDiscoveryController {
    private final JdbcTemplate jdbc;
    public LearnerDiscoveryController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/search")
    public List<SearchResult> search(@AuthenticationPrincipal CurrentUser user,
            @PathVariable String academyCode, @RequestParam("q") String query,
            @RequestParam(defaultValue="20") int limit) {
        UUID academy = academy(user, academyCode);
        String q = query == null ? "" : query.trim();
        if (q.length() < 2 || q.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Search query must be 2-100 characters");
        int safeLimit = Math.max(1,Math.min(limit,50));
        String pattern = "%" + q.replace("\\","\\\\").replace("%","\\%").replace("_","\\_") + "%";
        return jdbc.query("""
            SELECT kind,slug,title,summary,category_slug,rank_score FROM (
              SELECT 'TECHNOLOGY' kind,t.slug,t.name title,COALESCE(t.description,'') summary,
                     NULL::varchar category_slug,0 rank_score
              FROM technologies t WHERE t.academy_id=? AND t.status='PUBLISHED'
                AND (t.name ILIKE ? ESCAPE '\\' OR t.slug ILIKE ? ESCAPE '\\')
              UNION ALL
              SELECT 'CATEGORY',c.slug,c.name,COALESCE(c.description,''),c.slug,1
              FROM categories c JOIN technologies t ON t.id=c.technology_id
              WHERE t.academy_id=? AND t.status='PUBLISHED' AND c.status='PUBLISHED'
                AND (c.name ILIKE ? ESCAPE '\\' OR c.slug ILIKE ? ESCAPE '\\')
              UNION ALL
              SELECT 'LESSON',l.slug,r.title,COALESCE(r.summary,''),c.slug,2
              FROM lessons l JOIN lesson_revisions r ON r.id=l.published_revision_id
                JOIN categories c ON c.id=l.category_id JOIN technologies t ON t.id=c.technology_id
              WHERE t.academy_id=? AND l.status='PUBLISHED' AND c.status='PUBLISHED'
                AND (r.title ILIKE ? ESCAPE '\\' OR l.slug ILIKE ? ESCAPE '\\' OR COALESCE(r.summary,'') ILIKE ? ESCAPE '\\')
                AND NOT EXISTS (SELECT 1 FROM lesson_entitlement_requirements req WHERE req.lesson_id=l.id
                  AND NOT EXISTS (SELECT 1 FROM academy_entitlements e WHERE e.user_id=? AND e.academy_id=t.academy_id
                    AND e.entitlement_code=req.entitlement_code AND e.status IN ('ACTIVE','GRACE')
                    AND (e.valid_until IS NULL OR e.valid_until>now())))
            ) results ORDER BY rank_score,title LIMIT ?
            """,(rs,n)->new SearchResult(rs.getString("kind"),rs.getString("slug"),rs.getString("title"),
                rs.getString("summary"),rs.getString("category_slug")),academy,pattern,pattern,academy,pattern,pattern,
                academy,pattern,pattern,pattern,user.id(),safeLimit);
    }

    @GetMapping("/recommendations")
    public List<Recommendation> recommendations(@AuthenticationPrincipal CurrentUser user,
            @PathVariable String academyCode, @RequestParam(defaultValue="12") int limit) {
        UUID academy=academy(user,academyCode); int size=Math.max(1,Math.min(limit,30));
        return jdbc.query("""
            SELECT l.slug,r.title,COALESCE(r.summary,''),c.slug category_slug,
              CASE WHEN b.lesson_id IS NOT NULL THEN 0
                   WHEN p.lesson_id IS NOT NULL AND p.status<>'COMPLETED' THEN 1
                   WHEN prereq.completed_count>0 THEN 2 ELSE 3 END reason_rank,
              CASE WHEN b.lesson_id IS NOT NULL THEN 'BOOKMARKED'
                   WHEN p.lesson_id IS NOT NULL AND p.status<>'COMPLETED' THEN 'CONTINUE_LEARNING'
                   WHEN prereq.completed_count>0 THEN 'NEXT_IN_TRACK' ELSE 'POPULAR_START' END reason
            FROM lessons l JOIN lesson_revisions r ON r.id=l.published_revision_id
              JOIN categories c ON c.id=l.category_id JOIN technologies t ON t.id=c.technology_id
              LEFT JOIN learner_bookmarks b ON b.lesson_id=l.id AND b.user_id=?
              LEFT JOIN learner_lesson_progress p ON p.lesson_id=l.id AND p.user_id=?
              LEFT JOIN LATERAL (SELECT COUNT(*) completed_count FROM learner_lesson_progress px
                JOIN lessons lx ON lx.id=px.lesson_id JOIN categories cx ON cx.id=lx.category_id
                JOIN technologies tx ON tx.id=cx.technology_id
                WHERE px.user_id=? AND tx.academy_id=t.academy_id AND px.status='COMPLETED'
                  AND tx.id=t.id) prereq ON TRUE
            WHERE t.academy_id=? AND t.status='PUBLISHED' AND c.status='PUBLISHED' AND l.status='PUBLISHED'
              AND (p.lesson_id IS NULL OR p.status<>'COMPLETED')
              AND NOT EXISTS (SELECT 1 FROM lesson_entitlement_requirements req WHERE req.lesson_id=l.id
                AND NOT EXISTS (SELECT 1 FROM academy_entitlements e WHERE e.user_id=? AND e.academy_id=t.academy_id
                  AND e.entitlement_code=req.entitlement_code AND e.status IN ('ACTIVE','GRACE')
                  AND (e.valid_until IS NULL OR e.valid_until>now())))
            ORDER BY reason_rank,c.sort_order,r.title,l.id LIMIT ?
            """,(rs,n)->new Recommendation(rs.getString("slug"),rs.getString("title"),
                rs.getString(3),rs.getString("category_slug"),rs.getString("reason")),user.id(),user.id(),
                user.id(),academy,user.id(),size);
    }

    @GetMapping("/notification-preferences")
    public Preferences preferences(@AuthenticationPrincipal CurrentUser user,@PathVariable String academyCode) {
        UUID academy=academy(user,academyCode);
        return jdbc.query("""
            SELECT reminders_enabled,push_enabled,quiet_start,quiet_end,timezone
            FROM learner_notification_preferences WHERE user_id=? AND academy_id=?
            """,(rs,n)->new Preferences(rs.getBoolean(1),rs.getBoolean(2),
                rs.getTime(3)==null?null:rs.getTime(3).toLocalTime().toString(),
                rs.getTime(4)==null?null:rs.getTime(4).toLocalTime().toString(),rs.getString(5)),
            user.id(),academy).stream().findFirst().orElse(new Preferences(true,false,null,null,"UTC"));
    }

    @PutMapping("/notification-preferences")
    @Transactional
    public Preferences updatePreferences(@AuthenticationPrincipal CurrentUser user,
            @PathVariable String academyCode,@RequestBody Preferences request) {
        UUID academy=academy(user,academyCode);
        if(request==null || request.timezone()==null || request.timezone().length()>80)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Valid timezone is required");
        try { java.time.ZoneId.of(request.timezone()); } catch(Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown timezone");
        }
        if((request.quietStart()==null)!=(request.quietEnd()==null))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Quiet hours require both start and end");
        jdbc.update("""
            INSERT INTO learner_notification_preferences(user_id,academy_id,reminders_enabled,push_enabled,
              quiet_start,quiet_end,timezone,updated_at) VALUES (?,?,?,?,?::time,?::time,?,now())
            ON CONFLICT(user_id,academy_id) DO UPDATE SET reminders_enabled=EXCLUDED.reminders_enabled,
              push_enabled=EXCLUDED.push_enabled,quiet_start=EXCLUDED.quiet_start,quiet_end=EXCLUDED.quiet_end,
              timezone=EXCLUDED.timezone,updated_at=now()
            """,user.id(),academy,request.remindersEnabled(),request.pushEnabled(),
            request.quietStart(),request.quietEnd(),request.timezone());
        return preferences(user,academyCode);
    }

    @GetMapping("/revision-schedule")
    @Transactional
    public List<RevisionDue> revisionSchedule(@AuthenticationPrincipal CurrentUser user,
            @PathVariable String academyCode) {
        UUID academy=academy(user,academyCode);
        jdbc.update("""
            INSERT INTO learner_revision_schedules(user_id,academy_id,lesson_id,stage,due_at,last_completed_at)
            SELECT p.user_id,?,p.lesson_id,0,COALESCE(p.completed_at,now())+interval '1 day',p.completed_at
            FROM learner_lesson_progress p JOIN lessons l ON l.id=p.lesson_id
            JOIN categories c ON c.id=l.category_id JOIN technologies t ON t.id=c.technology_id
            WHERE p.user_id=? AND t.academy_id=? AND p.status='COMPLETED'
            ON CONFLICT(user_id,academy_id,lesson_id) DO NOTHING
            """,academy,user.id(),academy);
        jdbc.update("""
            INSERT INTO learner_notifications(id,user_id,academy_id,notification_type,dedupe_key,title,message,payload,status,scheduled_at)
            SELECT gen_random_uuid(),s.user_id,s.academy_id,'REVISION_DUE',
              'REVISION:'||s.lesson_id||':'||s.stage||':'||to_char(s.due_at AT TIME ZONE 'UTC','YYYYMMDDHH24MI'),
              'Revision reminder', 'Time to revisit '||l.title,
              jsonb_build_object('lessonSlug',l.slug,'stage',s.stage), 'PENDING',
              CASE WHEN prefs.quiet_start IS NOT NULL AND
                ((now() AT TIME ZONE prefs.timezone)::time BETWEEN prefs.quiet_start AND prefs.quiet_end)
                THEN (date_trunc('day',now() AT TIME ZONE prefs.timezone)+prefs.quiet_end) AT TIME ZONE prefs.timezone
                ELSE s.due_at END
            FROM learner_revision_schedules s JOIN lessons l ON l.id=s.lesson_id
            LEFT JOIN learner_notification_preferences prefs ON prefs.user_id=s.user_id AND prefs.academy_id=s.academy_id
            WHERE s.user_id=? AND s.academy_id=? AND s.due_at<=now()
              AND COALESCE(prefs.reminders_enabled,TRUE)=TRUE
            ON CONFLICT(user_id,academy_id,dedupe_key) DO NOTHING
            """,user.id(),academy);
        return jdbc.query("""
            SELECT l.slug,l.title,s.stage,s.due_at FROM learner_revision_schedules s
            JOIN lessons l ON l.id=s.lesson_id JOIN categories c ON c.id=l.category_id
            JOIN technologies t ON t.id=c.technology_id
            WHERE s.user_id=? AND s.academy_id=? AND t.academy_id=? AND l.status='PUBLISHED'
            ORDER BY s.due_at LIMIT 100
            """,(rs,n)->new RevisionDue(rs.getString(1),rs.getString(2),rs.getInt(3),rs.getTimestamp(4).toInstant()),
            user.id(),academy,academy);
    }

    @PostMapping("/revision-schedule/{lessonSlug}/reviewed")
    @Transactional
    public Map<String,Object> reviewed(@AuthenticationPrincipal CurrentUser user,
            @PathVariable String academyCode,@PathVariable String lessonSlug) {
        UUID academy=academy(user,academyCode);
        var rows=jdbc.query("""
            SELECT l.id FROM lessons l JOIN categories c ON c.id=l.category_id JOIN technologies t ON t.id=c.technology_id
            WHERE l.slug=? AND l.status='PUBLISHED' AND t.academy_id=?
            """,(rs,n)->rs.getObject(1,UUID.class),lessonSlug,academy);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Published academy lesson not found");
        UUID lesson=rows.getFirst();
        jdbc.update("""
            INSERT INTO learner_revision_schedules(user_id,academy_id,lesson_id,stage,due_at,last_completed_at)
            VALUES (?,?,?,1,now()+interval '3 days',now())
            ON CONFLICT(user_id,academy_id,lesson_id) DO UPDATE SET
              stage=LEAST(learner_revision_schedules.stage+1,5),
              due_at=now()+CASE LEAST(learner_revision_schedules.stage+1,5)
                WHEN 1 THEN interval '3 days' WHEN 2 THEN interval '7 days'
                WHEN 3 THEN interval '14 days' WHEN 4 THEN interval '30 days' ELSE interval '60 days' END,
              last_completed_at=now(),updated_at=now()
            """,user.id(),academy,lesson);
        return Map.of("lessonSlug",lessonSlug,"status","REVIEWED");
    }

    @GetMapping("/notifications")
    public List<NotificationItem> notifications(@AuthenticationPrincipal CurrentUser user,
            @PathVariable String academyCode,@RequestParam(defaultValue="30") int limit) {
        UUID academy=academy(user,academyCode);
        return jdbc.query("""
            SELECT id,title,message,payload::text,status,scheduled_at,created_at
            FROM learner_notifications WHERE user_id=? AND academy_id=? AND scheduled_at<=now()
            ORDER BY created_at DESC LIMIT ?
            """,(rs,n)->new NotificationItem(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),
                rs.getString(4),rs.getString(5),rs.getTimestamp(6).toInstant(),rs.getTimestamp(7).toInstant()),
            user.id(),academy,Math.max(1,Math.min(limit,100)));
    }

    @PostMapping("/notifications/{notificationId}/read")
    @Transactional
    public void markRead(@AuthenticationPrincipal CurrentUser user,@PathVariable String academyCode,
            @PathVariable UUID notificationId) {
        UUID academy=academy(user,academyCode);
        int count=jdbc.update("""
            UPDATE learner_notifications SET status='READ' WHERE id=? AND user_id=? AND academy_id=?
            """,notificationId,user.id(),academy);
        if(count==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Notification not found");
    }

    private UUID academy(CurrentUser user,String code) {
        if(code==null||!code.matches("[A-Z][A-Z0-9_]{1,39}"))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Academy not found");
        return jdbc.query("""
            SELECT a.id FROM academies a JOIN academy_memberships m ON m.academy_id=a.id
            WHERE a.code=? AND a.status='ACTIVE' AND m.user_id=? AND m.status='ACTIVE'
            """,(rs,n)->rs.getObject(1,UUID.class),code,user.id()).stream().findFirst()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Academy membership not found"));
    }
    public record SearchResult(String type,String slug,String title,String summary,String categorySlug) {}
    public record Recommendation(String lessonSlug,String title,String summary,String categorySlug,String reason) {}
    public record Preferences(boolean remindersEnabled,boolean pushEnabled,String quietStart,String quietEnd,String timezone) {}
    public record RevisionDue(String lessonSlug,String title,int stage,Instant dueAt) {}
    public record NotificationItem(UUID id,String title,String message,String payload,String status,Instant scheduledAt,Instant createdAt) {}
}
