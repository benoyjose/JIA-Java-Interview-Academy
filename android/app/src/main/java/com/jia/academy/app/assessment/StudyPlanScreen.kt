package com.jia.academy.app.assessment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
@Composable
fun StudyPlanScreen(plan: JiaStudyPlan, modifier: Modifier = Modifier) {
 Card(modifier.fillMaxWidth()) {
  Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
   Text("Your daily study plan", style = MaterialTheme.typography.titleMedium)
   Text(plan.dailyMinutes.toString() + " minute target · " + plan.tasks.sumOf { it.minutes } + " planned", style = MaterialTheme.typography.bodySmall)
   if (plan.tasks.isEmpty()) Text("Great work—no weak topics or unfinished lessons were returned.")
   plan.tasks.forEachIndexed { index, task ->
    Text((index + 1).toString() + ". " + task.title + " · " + task.minutes + " min", style = MaterialTheme.typography.bodyMedium)
    Text(task.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
   }
  }
 }
}
