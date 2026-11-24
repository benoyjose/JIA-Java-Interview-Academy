package com.jia.academy.learning.service;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
/** Converts due-review output and weak-topic evidence into a bounded, auditable session. */
@Service
public class LearningSessionOrchestrator {
 public record Topic(String id,String title,int weaknessScore,int estimatedMinutes){}
 public record SessionItem(String kind,String reference,String title,int minutes,String rationale){}
 public record SessionPlan(int budgetMinutes,int scheduledMinutes,List<SessionItem> items){}
 private final AdaptiveReviewScheduler scheduler;
 public LearningSessionOrchestrator(AdaptiveReviewScheduler scheduler){this.scheduler=Objects.requireNonNull(scheduler);}
 public SessionPlan compose(List<Topic> topics,List<AdaptiveReviewScheduler.Evidence> evidence,Instant now,int availableMinutes,int dailyGoalMinutes){
  if(availableMinutes<5||availableMinutes>480||dailyGoalMinutes<5||dailyGoalMinutes>240)throw new IllegalArgumentException("Session limits are outside supported bounds");
  int budget=Math.min(availableMinutes,dailyGoalMinutes);var items=new java.util.ArrayList<SessionItem>();
  var reviews=scheduler.schedule(evidence,now,Math.min(100,Math.max(1,budget/5)));
  int reviewBudget=Math.min(reviews.size(),Math.max(1,(int)Math.floor(budget*.35/5)));int used=0;
  for(var review:reviews.subList(0,reviewBudget)){items.add(new SessionItem("REVIEW",review.itemId(),review.itemId(),5,review.rationale()));used+=5;}
  var ranked=topics.stream().filter(t->t!=null&&t.id()!=null&&!t.id().isBlank()).sorted(java.util.Comparator.comparingInt(Topic::weaknessScore).reversed().thenComparing(Topic::id)).toList();
  for(var topic:ranked){if(used>=budget)break;int minutes=Math.min(Math.max(5,topic.estimatedMinutes()),Math.min(25,budget-used));items.add(new SessionItem("LEARN",topic.id(),topic.title(),minutes,"Weakness-weighted study"));used+=minutes;}
  if(items.isEmpty())items.add(new SessionItem("RECALL","daily-recap","Quick retrieval",Math.min(10,budget),"Fallback when no evidence is available"));
  return new SessionPlan(budget,used,List.copyOf(items));
 }
}
