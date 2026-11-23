package com.jia.academy.learning.api;
import com.jia.academy.learning.service.SpacedReviewScheduler;import com.jia.academy.security.CurrentUser;import java.time.LocalDate;import java.util.Map;import org.springframework.http.HttpStatus;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.web.bind.annotation.*;import org.springframework.web.server.ResponseStatusException;
@RestController @RequestMapping("/api/v1/me/review-schedule")
public class SpacedReviewScheduleController{
 private final SpacedReviewScheduler scheduler;public SpacedReviewScheduleController(SpacedReviewScheduler s){scheduler=s;}
 @PostMapping("/preview") public Map<String,Object> preview(@AuthenticationPrincipal CurrentUser user,@RequestBody GradeRequest req){
  if(user==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Unauthenticated");
  var state=scheduler.grade(req.rating(),req.repetitions(),req.easeFactor(),req.intervalDays(),req.reviewedOn()==null?LocalDate.now():req.reviewedOn());
  return Map.of("algorithmVersion",1,"repetitions",state.repetitions(),"easeFactor",state.easeFactor(),"intervalDays",state.intervalDays(),"dueDate",state.dueDate());
 }
 public record GradeRequest(int rating,int repetitions,double easeFactor,int intervalDays,LocalDate reviewedOn){}
}
