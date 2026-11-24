package com.jia.academy.learning.service;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
@Service
public class AdaptiveReviewScheduler {
 public record Evidence(String itemId,Instant lastReviewedAt,int consecutiveCorrect,int recentWrong,int confidencePercent){}
 public record ReviewItem(String itemId,Instant dueAt,int intervalDays,int priority,String rationale){}
 public List<ReviewItem> schedule(List<Evidence> evidence,Instant now,int limit){
  if(limit<1||limit>100)throw new IllegalArgumentException("limit must be 1..100");
  List<ReviewItem> due=new ArrayList<>();
  for(Evidence e:evidence){if(e.itemId()==null||e.itemId().isBlank()||e.lastReviewedAt()==null)continue;
   int interval=intervalDays(e.consecutiveCorrect(),e.recentWrong(),e.confidencePercent());
   Instant dueAt=e.lastReviewedAt().plus(Duration.ofDays(interval));if(dueAt.isAfter(now))continue;
   int confidence=Math.max(0,Math.min(100,e.confidencePercent()));
   int priority=Math.min(100,50+Math.max(0,e.recentWrong())*10+(100-confidence)/2);
   due.add(new ReviewItem(e.itemId(),dueAt,interval,priority,e.recentWrong()>0?"Recent incorrect answers":"Scheduled retention review"));
  }
  due.sort(Comparator.comparingInt(ReviewItem::priority).reversed().thenComparing(ReviewItem::dueAt).thenComparing(ReviewItem::itemId));
  return List.copyOf(due.subList(0,Math.min(limit,due.size())));
 }
 public int intervalDays(int streak,int wrong,int confidence){int c=Math.max(0,Math.min(100,confidence));if(wrong>0||c<50||streak<=0)return 1;if(c<75||streak==1)return 2;if(streak==2)return 4;return Math.min(30,4*(1+Math.min(6,streak-2)));}
}
