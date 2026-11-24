package com.jia.academy.app.assessment
import java.time.Duration
import java.time.Instant
data class PracticeEvidence(val id:String,val lastReviewedAt:Instant?,val correctStreak:Int,val recentWrong:Int,val confidencePercent:Int)
data class PracticeQueueItem(val id:String,val dueAt:Instant,val intervalDays:Int,val priority:Int,val reason:String)
object PracticeQueuePolicy {
 fun intervalDays(streak:Int,wrong:Int,confidence:Int):Int {val c=confidence.coerceIn(0,100);if(wrong>0||c<50||streak<=0)return 1;if(c<75||streak==1)return 2;if(streak==2)return 4;return (4*(1+(streak-2).coerceAtMost(6))).coerceAtMost(30)}
 fun due(evidence:List<PracticeEvidence>,now:Instant,limit:Int=30):List<PracticeQueueItem>{require(limit in 1..100)
  return evidence.mapNotNull{e->val last=e.lastReviewedAt?:return@mapNotNull null;if(e.id.isBlank())return@mapNotNull null
   val days=intervalDays(e.correctStreak,e.recentWrong,e.confidencePercent);val due=last.plus(Duration.ofDays(days.toLong()));if(due.isAfter(now))return@mapNotNull null
   PracticeQueueItem(e.id,due,days,(50+e.recentWrong.coerceAtLeast(0)*10+(100-e.confidencePercent.coerceIn(0,100))/2).coerceAtMost(100),if(e.recentWrong>0)"Revisit a missed concept" else "Scheduled recall")
  }.sortedWith(compareByDescending<PracticeQueueItem>{it.priority}.thenBy{it.dueAt}.thenBy{it.id}).take(limit)}
}
