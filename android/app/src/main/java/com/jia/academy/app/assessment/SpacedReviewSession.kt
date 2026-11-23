package com.jia.academy.app.assessment
import java.time.LocalDate
data class ReviewCard(val lessonSlug:String,val title:String,val prompt:String,val answer:String)
data class ReviewProgress(val index:Int,val completed:Int,val total:Int,val finished:Boolean)
class SpacedReviewSession(cards:List<ReviewCard>){
 private val queue=cards.toList();private var cursor=0;private var completed=0;private var revealed=false
 fun current():ReviewCard?=queue.getOrNull(cursor)
 fun reveal():Boolean{if(current()==null)return false;revealed=true;return true}
 fun isAnswerRevealed()=revealed
 fun grade(rating:Int):ReviewProgress{require(rating in 0..5);check(current()!=null){"Session complete"};check(revealed){"Reveal before grading"};cursor++;completed++;revealed=false;return progress()}
 fun progress()=ReviewProgress(cursor,completed,queue.size,cursor>=queue.size)
 fun nextDue(rating:Int,today:LocalDate):LocalDate{require(rating in 0..5);return today.plusDays(if(rating<3)1 else if(rating==3)3 else 6)}
}
