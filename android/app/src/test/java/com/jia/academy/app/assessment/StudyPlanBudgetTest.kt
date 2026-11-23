package com.jia.academy.app.assessment
import org.junit.Assert.assertEquals
import org.junit.Test
class StudyPlanBudgetTest {
 @Test fun clampsAndNeverOverallocates() {
  assertEquals(10, StudyPlanBudget.clamp(0)); assertEquals(180, StudyPlanBudget.clamp(500))
  val tasks = listOf(JiaStudyPlanTask("TOPIC","Concurrency",null,"concurrency",15,"weak"), JiaStudyPlanTask("LESSON","Streams","streams",null,20,"resume"))
  val plan = StudyPlanBudget.allocate(tasks,20)
  assertEquals(20, plan.sumOf { it.minutes }); assertEquals(5, plan.last().minutes)
 }
 @Test fun emptyPlanRemainsEmpty() { assertEquals(emptyList<JiaStudyPlanTask>(), StudyPlanBudget.allocate(emptyList(),30)) }
}
