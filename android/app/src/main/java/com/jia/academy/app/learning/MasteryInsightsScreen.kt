package com.jia.academy.app.learning
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class TopicMastery(val slug:String,val name:String,val answered:Int,val correct:Int,val completedLessons:Int) {
 val accuracy:Int get()=if(answered<=0) 0 else (100.0*correct/answered).toInt()
 val band:String get()=when { answered==0->"Not assessed";accuracy>=85->"Mastered";accuracy>=60->"Practicing";else->"Developing" }
}
@Composable fun MasteryInsightsScreen(topics:List<TopicMastery>,modifier:Modifier=Modifier){
 Column(modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("Skill mastery",style=MaterialTheme.typography.headlineSmall)
  Text("Scores are based on submitted objective answers; unopened lessons do not count as mastery.")
  if(topics.isEmpty()) Text("Complete a quiz to begin building your mastery profile.")
  topics.sortedBy{if(it.answered==0)-1 else it.accuracy}.forEach{topic->
   Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
    Text(topic.name,style=MaterialTheme.typography.titleMedium)
    Text(topic.band+" · "+topic.accuracy+"% accuracy")
    LinearProgressIndicator(progress={topic.accuracy/100f},modifier=Modifier.fillMaxWidth())
    Text(topic.correct.toString()+" correct of "+topic.answered+" answers · "+topic.completedLessons+" lessons completed",style=MaterialTheme.typography.bodySmall)
   }}
  }
 }
}