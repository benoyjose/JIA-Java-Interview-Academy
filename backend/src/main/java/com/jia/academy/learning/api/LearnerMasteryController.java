package com.jia.academy.learning.api;

import com.jia.academy.security.CurrentUser;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Learner-scoped, explainable mastery snapshot; never accepts a user id from the client. */
@RestController
@RequestMapping("/api/v1/me/mastery")
public class LearnerMasteryController {
 private final JdbcTemplate jdbc;
 public LearnerMasteryController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @GetMapping public Map<String,Object> summary(@AuthenticationPrincipal CurrentUser user){
  if(user==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in required");
  var topics=jdbc.query("""
   SELECT c.slug,c.name,COUNT(aa.id) answers,
    SUM(CASE WHEN aa.is_correct THEN 1 ELSE 0 END) correct,
    COUNT(DISTINCT lp.lesson_id) completed
   FROM categories c JOIN technologies t ON t.id=c.technology_id
   LEFT JOIN questions q ON q.category_id=c.id
   LEFT JOIN quiz_attempt_questions aq ON aq.question_id=q.id
   LEFT JOIN quiz_attempt_answers aa ON aa.attempt_id=aq.attempt_id AND aa.position=aq.position
   LEFT JOIN quiz_attempts a ON a.id=aa.attempt_id AND a.user_id=? AND a.status='SUBMITTED'
   LEFT JOIN lessons l ON l.category_id=c.id
   LEFT JOIN learner_lesson_progress lp ON lp.lesson_id=l.id AND lp.user_id=? AND lp.status='COMPLETED'
   WHERE t.academy_id=? GROUP BY c.slug,c.name ORDER BY c.name
   """,(rs,n)->Map.of("topicSlug",rs.getString("slug"),"topicName",rs.getString("name"),
      "answers",rs.getLong("answers"),"correct",rs.getLong("correct"),"completedLessons",rs.getLong("completed")),user.id(),user.id(),java.util.UUID.fromString("50000000-0000-0000-0000-000000000001"));
  return Map.of("modelVersion",1,"topics",topics);
 }
}