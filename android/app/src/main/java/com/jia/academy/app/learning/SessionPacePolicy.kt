package com.jia.academy.app.learning
enum class PaceAdvice { ON_TRACK,SLOW_DOWN,SPEED_UP,TAKE_BREAK }
object SessionPacePolicy {
 fun advise(elapsedMinutes:Int,plannedMinutes:Int,answered:Int,total:Int,consecutiveWrong:Int):PaceAdvice {
  require(elapsedMinutes>=0&&plannedMinutes>0&&answered>=0&&total>=0&&answered<=total&&consecutiveWrong>=0)
  if(consecutiveWrong>=3)return PaceAdvice.TAKE_BREAK
  if(total==0||answered==0)return if(elapsedMinutes>plannedMinutes)PaceAdvice.SLOW_DOWN else PaceAdvice.ON_TRACK
  val expected=plannedMinutes.toDouble()*answered/total
  return when {elapsedMinutes>expected*1.5->PaceAdvice.SLOW_DOWN;elapsedMinutes<expected*0.5&&answered>1->PaceAdvice.SPEED_UP;else->PaceAdvice.ON_TRACK}
 }
}