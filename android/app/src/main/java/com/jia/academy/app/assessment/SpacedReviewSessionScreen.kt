package com.jia.academy.app.assessment
import androidx.compose.foundation.layout.*;import androidx.compose.material3.*;import androidx.compose.runtime.*;import androidx.compose.ui.Modifier;import androidx.compose.ui.unit.dp
@Composable fun SpacedReviewSessionScreen(session:SpacedReviewSession,modifier:Modifier=Modifier,onComplete:()->Unit={}){
 var progress by remember{mutableStateOf(session.progress())};var revealed by remember{mutableStateOf(false)};val card=session.current()
 Card(modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
 Text("Review session",style=MaterialTheme.typography.titleMedium);LinearProgressIndicator(progress=if(progress.total==0)1f else progress.completed.toFloat()/progress.total,modifier=Modifier.fillMaxWidth())
 if(card==null||progress.finished){Text("Session complete");Text(progress.completed.toString()+" cards reviewed");Button(onClick=onComplete){Text("Done")}}
 else{Text(card.title,style=MaterialTheme.typography.titleLarge);Text(card.prompt);if(revealed)Text(card.answer)
 Button(onClick={revealed=session.reveal()},enabled=!revealed){Text("Reveal answer")}
 if(revealed)Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){(0..5).forEach{rating->OutlinedButton(onClick={progress=session.grade(rating);revealed=false}){Text(rating.toString())}}}
 }
 }}
}
