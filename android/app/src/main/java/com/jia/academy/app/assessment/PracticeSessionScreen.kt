package com.jia.academy.app.assessment
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
@Composable fun PracticeSessionScreen(controller:PracticeSessionController,onExit:()->Unit){
 val state by controller.state.collectAsState()
 LaunchedEffect(controller){controller.state.collectLatest{}}
 if(state.completed){Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center){Text("Session complete",style=MaterialTheme.typography.headlineMedium);Text("Score ${state.score} / ${state.prompts.size}");Button(onClick=onExit){Text("Return to learning")}};return}
 val prompt=state.current?:run{Column(Modifier.padding(24.dp)){Text("No questions available");Button(onClick=onExit){Text("Back")}};return}
 Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("Question ${state.index+1} of ${state.prompts.size}",style=MaterialTheme.typography.labelLarge);Text(prompt.question,style=MaterialTheme.typography.headlineSmall)
  prompt.options.forEachIndexed{index,option->OutlinedButton(onClick={controller.answer(index)},enabled=!state.submitted,modifier=Modifier.fillMaxWidth()){Text(option)}}
  if(state.submitted)Text(prompt.explanation)
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={controller.submit()},enabled=!state.submitted){Text("Check answer")};Button(onClick={controller.next()},enabled=state.submitted){Text(if(state.index==state.prompts.lastIndex)"Finish" else "Next")}}
 }
}
