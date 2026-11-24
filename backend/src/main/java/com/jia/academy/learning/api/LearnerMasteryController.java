package com.jia.academy.learning.api;
import com.jia.academy.security.CurrentUser;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Authenticated learner-only mastery evidence. No caller-supplied identity is accepted. */
@RestController
@RequestMapping("/api/v1/me/mastery")
public class LearnerMasteryController {
 private final JdbcTemplate jdbc;
 public LearnerMasteryController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @GetMapping public Map<String,Object> summary(@AuthenticationPrincipal CurrentUser user){
  if(user==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in required");
  var academy=UUID.fromString("50000000-0000-0000-0000-000000000001");
  var topics=jdbc.query("""
   SELECT c.slug,c.name,
    (SELECT COUNT(*) FROM quiz_attempt_answers aa
      JOIN quiz_attempts a ON a.id=aa.attempt_id
      JOIN quiz_attempt_questions aq ON aq.attempt_id=aa.attempt_id AND aq.position=aa.position
      JOIN questions q ON q.id=aq.question_id
      WHERE q.category_id=c.id AND a.user_id=? AND a.academy_id=? AND a.status='SUBMITTED') answers,
    (SELECT COUNT(*) FROM quiz_attempt_answers aa
      JOIN quiz_attempts a ON a.id=aa.attempt_id
      JOIN quiz_attempt_questions aq ON aq.attempt_id=aa.attempt_id AND aq.position=aa.position
      JOIN questions q ON q.id=aq.question_id
      WHERE q.category_id=c.id AND a.user_id=? AND a.academy_id=? AND a.status='SUBMITTED' AND aa.is_correct=TRUE) correct,
    (SELECT COUNT(DISTINCT lp.lesson_id) FROM learner_lesson_progress lp
      JOIN lessons l ON l.id=lp.lesson_id
      WHERE l.category_id=c.id AND lp.user_id=? AND lp.status='COMPLETED') completed
   FROM categories c JOIN technologies t ON t.id=c.technology_id
   WHERE t.academy_id=? ORDER BY c.name
   """,(rs,n)->Map.of("topicSlug",rs.getString("slug"),"topicName",rs.getString("name"),
     "answers",rs.getLong("answers"),"correct",rs.getLong("correct"),"completedLessons",rs.getLong("completed")),
    user.id(),academy,user.id(),academy,user.id(),academy);
  return Map.of("modelVersion",1,"topics",topics);
 }
}