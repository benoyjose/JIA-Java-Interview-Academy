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
@Composable fun PracticeQueueScreen(items:List<PracticeQueueItem>,modifier:Modifier=Modifier){Card(modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
 Text("Review queue",style=MaterialTheme.typography.titleMedium);Text(items.size.toString()+" concepts due",style=MaterialTheme.typography.bodySmall)
 if(items.isEmpty())Text("You're caught up. Reviews appear as learning evidence becomes due.")
 items.forEachIndexed{index,item->Text((index+1).toString()+". "+item.id+" · priority "+item.priority);Text(item.reason,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)}
}}}
