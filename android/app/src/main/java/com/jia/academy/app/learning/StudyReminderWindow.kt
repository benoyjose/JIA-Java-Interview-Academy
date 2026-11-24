package com.jia.academy.app.learning
import java.time.LocalTime
data class StudyReminderWindow(val shouldNotify:Boolean,val nextAllowed:LocalTime?)
object StudyReminderPlanner {
 fun evaluate(now:LocalTime, quietStart:LocalTime, quietEnd:LocalTime):StudyReminderWindow {
  val quiet=if(quietStart==quietEnd) false else if(quietStart<quietEnd) now>=quietStart&&now<quietEnd else now>=quietStart||now<quietEnd
  if(!quiet) return StudyReminderWindow(true,null)
  return StudyReminderWindow(false,quietEnd)
 }
}