package com.jia.academy.learning.api;
import com.jia.academy.learning.service.AdaptiveReviewScheduler;
import com.jia.academy.security.CurrentUser;
import java.time.Instant;import java.util.List;import java.util.Map;import java.util.UUID;
import org.springframework.http.HttpStatus;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;import org.springframework.web.server.ResponseStatusException;
/** Server-computed queue scoped exclusively to the authenticated learner. */
@RestController @RequestMapping("/api/v1/me/reviews")
public class LearnerReviewQueueController {
 private final JdbcTemplate jdbc;private final AdaptiveReviewScheduler scheduler;
 public LearnerReviewQueueController(JdbcTemplate jdbc,AdaptiveReviewScheduler scheduler){this.jdbc=jdbc;this.scheduler=scheduler;}
 @GetMapping public Map<String,Object> due(@AuthenticationPrincipal CurrentUser user,@RequestParam(defaultValue="30") int limit){
  if(user==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in required");
  if(limit<1||limit>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"limit must be 1..100");
  UUID academy=UUID.fromString("50000000-0000-0000-0000-000000000001");
  List<AdaptiveReviewScheduler.Evidence> evidence=jdbc.query("""
   SELECT q.id::text item_id,MAX(a.submitted_at) reviewed_at,
    COUNT(*) FILTER(WHERE aa.is_correct) correct_count,
    COUNT(*) FILTER(WHERE aa.is_correct=FALSE) wrong_count,
    COALESCE(ROUND(AVG(CASE WHEN aa.is_correct THEN 100.0 ELSE 0.0 END)),0) confidence
   FROM quiz_attempt_answers aa JOIN quiz_attempts a ON a.id=aa.attempt_id
   JOIN quiz_attempt_questions aq ON aq.attempt_id=aa.attempt_id AND aq.position=aa.position
   JOIN questions q ON q.id=aq.question_id
   WHERE a.user_id=? AND a.academy_id=? AND a.status='SUBMITTED'
   GROUP BY q.id ORDER BY MAX(a.submitted_at) DESC LIMIT 500
   """,(rs,n)->new AdaptiveReviewScheduler.Evidence(rs.getString("item_id"),rs.getTimestamp("reviewed_at").toInstant(),rs.getInt("correct_count"),rs.getInt("wrong_count"),rs.getInt("confidence")),user.id(),academy);
  var items=scheduler.schedule(evidence,Instant.now(),limit);
  return Map.of("generatedAt",Instant.now(),"limit",limit,"items",items);
 }
}
