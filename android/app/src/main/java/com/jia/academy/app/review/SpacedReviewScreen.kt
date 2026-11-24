package com.jia.academy.app.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant

@Composable
fun SpacedReviewScreen(initial: ReviewSessionState, onGrade: (ReviewGrade) -> Unit, modifier: Modifier = Modifier) {
    var state by remember(initial) { mutableStateOf(initial) }
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.isComplete) {
                Text("Review complete", style = MaterialTheme.typography.titleLarge)
                Text("${state.completed.size} cards reviewed")
            } else {
                val card = state.current!!
                Text("Card ${state.index + 1} of ${state.cards.size}", style = MaterialTheme.typography.labelMedium)
                Text(card.prompt, style = MaterialTheme.typography.titleLarge)
                if (state.answerVisible) Text(card.answer, style = MaterialTheme.typography.bodyLarge)
                else Button(onClick = { state = state.reveal() }) { Text("Reveal answer") }
                if (state.answerVisible) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (0..5).forEach { grade -> Button(onClick = {
                        val next = state.grade(grade, Instant.now())
                        next.completed.lastOrNull()?.let(onGrade)
                        state = next
                    }) { Text(grade.toString()) } }
                }
            }
        }
    }
}
