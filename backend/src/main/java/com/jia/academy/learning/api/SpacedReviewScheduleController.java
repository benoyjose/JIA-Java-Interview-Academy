package com.jia.academy.learning.api;
import com.jia.academy.learning.service.SpacedReviewScheduler;
import com.jia.academy.security.CurrentUser;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestController
@RequestMapping("/api/v1/me/review-schedule")
public class SpacedReviewScheduleController {
 private static final UUID ACADEMY=UUID.fromString("50000000-0000-0000-0000-000000000001");
 private final SpacedReviewScheduler scheduler;private final JdbcTemplate jdbc;
 public SpacedReviewScheduleController(SpacedReviewScheduler s,JdbcTemplate j){scheduler=s;jdbc=j;}
 @GetMapping("/due") public Map<String,Object> due(@AuthenticationPrincipal CurrentUser user){
  requireUser(user);List<Map<String,Object>> items=jdbc.queryForList("""
   SELECT l.slug,l.title,r.repetitions,r.ease_factor,r.interval_days,r.due_date
   FROM learner_spaced_reviews r JOIN lessons l ON l.id=r.lesson_id
   JOIN categories c ON c.id=l.category_id JOIN technologies t ON t.id=c.technology_id
   WHERE r.user_id=? AND t.academy_id=? AND l.status='PUBLISHED' AND r.due_date<=CURRENT_DATE
   ORDER BY r.due_date,l.slug LIMIT 50
   """,user.id(),ACADEMY);
  return Map.of("dueCount",items.size(),"items",items);
 }
 @PostMapping("/{lessonSlug}/grade") public Map<String,Object> grade(@AuthenticationPrincipal CurrentUser user,@PathVariable String lessonSlug,@RequestBody ReviewRatingRequest request){
  requireUser(user);if(request.rating()<0||request.rating()>5)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"rating must be 0..5");
  UUID lesson=jdbc.query("SELECT l.id FROM lessons l JOIN categories c ON c.id=l.category_id JOIN technologies t ON t.id=c.technology_id WHERE l.slug=? AND l.status='PUBLISHED' AND t.academy_id=?",(rs)->rs.next()?UUID.fromString(rs.getString(1)):null,lessonSlug,ACADEMY);
  if(lesson==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Published lesson not found");
  Map<String,Object> old=jdbc.query("SELECT repetitions,ease_factor,interval_days FROM learner_spaced_reviews WHERE user_id=? AND lesson_id=?",(rs)->rs.next()?Map.of("repetitions",rs.getInt(1),"ease",rs.getDouble(2),"interval",rs.getInt(3)):Map.<String,Object>of(),user.id(),lesson);
  var state=scheduler.grade(request.rating(),(int)old.getOrDefault("repetitions",0),(double)old.getOrDefault("ease",2.5),(int)old.getOrDefault("interval",0),LocalDate.now());
  jdbc.update("""
   INSERT INTO learner_spaced_reviews(user_id,lesson_id,repetitions,ease_factor,interval_days,last_rating,last_reviewed_at,due_date,updated_at)
   VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP)
   ON CONFLICT(user_id,lesson_id) DO UPDATE SET repetitions=EXCLUDED.repetitions,ease_factor=EXCLUDED.ease_factor,
   interval_days=EXCLUDED.interval_days,last_rating=EXCLUDED.last_rating,last_reviewed_at=CURRENT_TIMESTAMP,due_date=EXCLUDED.due_date,updated_at=CURRENT_TIMESTAMP
   """,user.id(),lesson,state.repetitions(),state.easeFactor(),state.intervalDays(),request.rating(),state.dueDate());
  return Map.of("lessonSlug",lessonSlug,"rating",request.rating(),"repetitions",state.repetitions(),"easeFactor",state.easeFactor(),"intervalDays",state.intervalDays(),"dueDate",state.dueDate(),"algorithmVersion",1);
 }
 @PostMapping("/preview") public Map<String,Object> preview(@AuthenticationPrincipal CurrentUser user,@RequestBody GradeRequest req){
  requireUser(user);var state=scheduler.grade(req.rating(),req.repetitions(),req.easeFactor(),req.intervalDays(),req.reviewedOn()==null?LocalDate.now():req.reviewedOn());
  return Map.of("algorithmVersion",1,"repetitions",state.repetitions(),"easeFactor",state.easeFactor(),"intervalDays",state.intervalDays(),"dueDate",state.dueDate());
 }
 private static void requireUser(CurrentUser user){if(user==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Unauthenticated");}
 public record GradeRequest(int rating,int repetitions,double easeFactor,int intervalDays,LocalDate reviewedOn){}\n public record ReviewRatingRequest(int rating){}
}
