package com.jia.academy.app.assessment
data class JiaStudyPlanTask(val kind: String, val title: String, val lessonSlug: String?, val topicSlug: String?, val minutes: Int, val reason: String)
data class JiaStudyPlan(val dailyMinutes: Int, val tasks: List<JiaStudyPlanTask>)
object StudyPlanBudget {
 fun clamp(minutes: Int): Int = minutes.coerceIn(10, 180)
 fun allocate(tasks: List<JiaStudyPlanTask>, requestedMinutes: Int): List<JiaStudyPlanTask> {
  var budget = clamp(requestedMinutes)
  return tasks.mapNotNull { task ->
   if (budget <= 0) return@mapNotNull null
   val amount = minOf(task.minutes.coerceAtLeast(1), budget); budget -= amount
   task.copy(minutes = amount)
  }
 }
}
