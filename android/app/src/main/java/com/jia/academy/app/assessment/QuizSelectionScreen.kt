package com.jia.academy.app.assessment

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.dp
import com.jia.academy.app.BuildConfig
import com.jia.academy.app.catalogue.CatalogueRepository
import com.jia.academy.app.catalogue.QuizAvailability
import com.jia.academy.app.auth.SecureSessionStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

@Composable
private fun CodeSnippet(language: String?, source: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(language?.uppercase() ?: "CODE", style = MaterialTheme.typography.labelSmall)
            Text(
                source,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            )
        }
    }
}

@Composable
fun QuizSelectionScreen(repository: CatalogueRepository, signedIn: Boolean, modifier: Modifier) {
    val levels = listOf(
        "BEGINNER" to "Beginner", "NOVICE" to "Novice",
        "PROFESSIONAL" to "Professional", "EXPERT" to "Expert"
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val quizRepository = remember(context) { QuizRepository(context) }
    var selected by remember { mutableStateOf("BEGINNER") }
    var selectedMode by remember { mutableStateOf(JiaQuizMode.PRACTICE) }
    var selectedDuration by remember { mutableStateOf(1800) }
    var remainingSeconds by remember { mutableStateOf<Int?>(null) }
    var availability by remember { mutableStateOf<QuizAvailability?>(null) }
    var attempt by remember { mutableStateOf<JiaQuizAttempt?>(null) }
    var answers by remember { mutableStateOf<Map<Int, Set<String>>>(emptyMap()) }
    var result by remember { mutableStateOf<JiaQuizResult?>(null) }
    var history by remember { mutableStateOf<List<JiaQuizHistoryItem>?>(null) }
    var performance by remember { mutableStateOf<JiaQuizPerformance?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selected) {
        loading = true
        error = null
        runCatching { repository.getQuizAvailability(BuildConfig.JIA_API_BASE_URL, selected) }
            .onSuccess { availability = it }.onFailure { error = it.message ?: "Unable to check quiz availability." }
        loading = false
    }

    val activeAttempt = attempt
    val completed = result
    LaunchedEffect(activeAttempt?.id, answers) {
        val current = activeAttempt
        if (current?.mode == JiaQuizMode.TIMED_MOCK) {
            val token = com.jia.academy.app.auth.SecureSessionStore(context).accessToken()
            if (!token.isNullOrBlank()) {
                delay(500)
                runCatching { quizRepository.saveAnswers(BuildConfig.JIA_API_BASE_URL, token, current, answers) }
                    .onFailure { error = it.message ?: "Unable to save timed mock progress." }
            }
        }
    }
    LaunchedEffect(activeAttempt?.deadlineAt) {
        val deadline = activeAttempt?.deadlineAt
        if (deadline != null) {
            while (result == null) {
                val seconds = Duration.between(Instant.now(), Instant.parse(deadline)).seconds.coerceAtLeast(0).toInt()
                remainingSeconds = seconds
                if (seconds == 0) {
                    val token = com.jia.academy.app.auth.SecureSessionStore(context).accessToken()
                    if (!token.isNullOrBlank()) {
                        loading = true
                        runCatching { quizRepository.submit(BuildConfig.JIA_API_BASE_URL, token, activeAttempt!!, answers) }
                            .onSuccess { result = it }
                            .onFailure { error = it.message ?: "Timed mock expired; unable to submit saved answers." }
                        loading = false
                    }
                    break
                }
                delay(1000)
            }
        } else remainingSeconds = null
    }
    val answeredCount = activeAttempt?.questions?.count { !answers[it.position].isNullOrEmpty() } ?: 0
    val totalQuestions = activeAttempt?.questions?.size ?: 0
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Java practice", style = MaterialTheme.typography.headlineSmall)
        when {
            completed != null && activeAttempt != null -> {
                Text("Quiz result", style = MaterialTheme.typography.titleLarge)
                if (completed.timedOut) Text("Time expired — your saved answers were submitted automatically.",
                    color = MaterialTheme.colorScheme.error)
                Text("${completed.correctCount} / ${completed.questionCount} correct",
                    style = MaterialTheme.typography.headlineMedium)
                Text("Score: ${completed.scorePercent}%")
                activeAttempt.questions.forEach { question ->
                    val review = completed.explanations[question.position]
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Q${question.position}. ${question.prompt}")
                            if (!question.codeSource.isNullOrBlank()) CodeSnippet(question.codeLanguage, question.codeSource)
                            Text(if (review?.first == true) "Correct" else "Incorrect",
                                color = if (review?.first == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            if (!review?.second.isNullOrBlank()) Text(review!!.second)
                        }
                    }
                }
                Button(onClick = { attempt = null; result = null; answers = emptyMap() }) { Text("Back to difficulty selection") }
            }
            activeAttempt != null -> {
                Text("${levels.first { it.first == activeAttempt.difficulty }.second} · ${activeAttempt.mode.displayName} · ${activeAttempt.questions.size} questions")
                if (activeAttempt.mode == JiaQuizMode.DAILY_CHALLENGE) {
                    Text("Daily challenge · ${activeAttempt.challengeDate ?: "Today"} · one challenge per day")
                }
                if (activeAttempt.mode == JiaQuizMode.TIMED_MOCK) {
                    val seconds = remainingSeconds ?: activeAttempt.durationLimitSeconds ?: 0
                    Text("Time remaining: ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (seconds <= 60) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(activeAttempt.questions, key = { it.position }) { question ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Question ${question.position}", style = MaterialTheme.typography.labelLarge)
                                if (question.prompt.isNotBlank()) {
                                    Text(question.prompt, style = MaterialTheme.typography.titleMedium)
                                }
                                if (!question.codeSource.isNullOrBlank()) {
                                    CodeSnippet(question.codeLanguage, question.codeSource)
                                }
                                val multi = question.questionType == "MULTI_SELECT"
                                Text(if (multi) "Select all that apply" else "Select one answer",
                                    style = MaterialTheme.typography.bodySmall)
                                question.options.forEach { (value, label) ->
                                    val checked = value in answers[question.position].orEmpty()
                                    FilterChip(
                                        selected = checked,
                                        onClick = {
                                            val current = answers[question.position].orEmpty()
                                            val next = if (multi) {
                                                if (checked) current - value else current + value
                                            } else setOf(value)
                                            answers = answers + (question.position to next)
                                        },
                                        label = { Text("$value. $label") }
                                    )
                                }
                                question.hint?.let { Text("Hint: $it", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
                Text(
                    "$answeredCount of $totalQuestions questions answered",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (answeredCount == totalQuestions) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    enabled = !loading && (activeAttempt.mode == JiaQuizMode.TIMED_MOCK || answeredCount == totalQuestions),
                    onClick = {
                        val token = com.jia.academy.app.auth.SecureSessionStore(context).accessToken()
                        if (token.isNullOrBlank()) { error = "Please sign in again to submit this quiz."; return@Button }
                        loading = true
                        error = null
                        scope.launch {
                            runCatching { quizRepository.submit(BuildConfig.JIA_API_BASE_URL, token, activeAttempt, answers) }
                                .onSuccess { result = it }.onFailure { error = it.message ?: "Unable to submit quiz." }
                            loading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (loading) "Submitting…" else if (activeAttempt.mode == JiaQuizMode.TIMED_MOCK) "Submit mock interview" else "Submit all answers") }
            }
            else -> {
                Text("Quiz mode", style = MaterialTheme.typography.titleMedium)
                JiaQuizMode.learnerOptions.forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { selectedMode = mode },
                        label = { Text(mode.displayName) }
                    )
                }
                if (selectedMode == JiaQuizMode.DAILY_CHALLENGE) {
                    Text("A fixed challenge is shared for your account for the day. Reopening it returns the same questions and attempt.", style = MaterialTheme.typography.bodySmall)
                }
                if (selectedMode == JiaQuizMode.WEAK_AREA) {
                    Text("Questions are prioritized from categories where your previous submitted answers show the lowest accuracy. Categories without history are considered after measured weak areas.", style = MaterialTheme.typography.bodySmall)
                }
                if (selectedMode == JiaQuizMode.TIMED_MOCK) {
                    Text("Mock duration", style = MaterialTheme.typography.titleMedium)
                    listOf(900 to "15 minutes", 1800 to "30 minutes", 3600 to "60 minutes").forEach { (seconds, label) ->
                        FilterChip(selected = selectedDuration == seconds,
                            onClick = { selectedDuration = seconds }, label = { Text(label) })
                    }
                }
                Text("Choose your difficulty", style = MaterialTheme.typography.titleMedium)
                levels.forEach { (value, label) ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(selected == value, onClick = { selected = value }, label = { Text(label) })
                            Text(when (value) {
                                "BEGINNER" -> "Fundamentals, definitions and direct examples"
                                "NOVICE" -> "Practical application and common code patterns"
                                "PROFESSIONAL" -> "Advanced Java, Spring and problem-solving"
                                else -> "Architecture, JVM internals and distributed systems"
                            }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                when {
                    loading -> CircularProgressIndicator()
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    availability != null -> Text("${availability!!.availableQuestionCount} published questions available.")
                }
                if (!signedIn) {
                    Text("Sign in is required to start and save a quiz. You can continue browsing lessons without an account.")
                } else {
                    Button(
                        enabled = !loading && (availability?.availableQuestionCount ?: 0) > 0,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val token = com.jia.academy.app.auth.SecureSessionStore(context).accessToken()
                            if (token.isNullOrBlank()) { error = "Please sign in again."; return@Button }
                            loading = true
                            error = null
                            val count = minOf(10, availability?.availableQuestionCount ?: 0)
                            scope.launch {
                                runCatching { quizRepository.start(BuildConfig.JIA_API_BASE_URL, token, selected, count, selectedMode, selectedDuration) }
                                    .onSuccess { attempt = it; answers = emptyMap(); result = null }
                                    .onFailure { error = it.message ?: "Unable to start quiz." }
                                loading = false
                            }
                        }
                    ) { Text("Start ${selectedMode.displayName.lowercase()} quiz") }
                    TextButton(onClick = {
                        val token = com.jia.academy.app.auth.SecureSessionStore(context).accessToken()
                        if (token.isNullOrBlank()) { error = "Please sign in again."; return@TextButton }
                        loading = true
                        scope.launch {
                            runCatching { quizRepository.performance(BuildConfig.JIA_API_BASE_URL, token) }
                                .onSuccess { performance = it }.onFailure { error = it.message ?: "Unable to load performance." }
                            loading = false
                        }
                    }) { Text("View my performance") }
                    performance?.let { report ->
                        Text("Performance by difficulty", style = MaterialTheme.typography.titleMedium)
                        report.levels.forEach { level ->
                            Text("${level.difficulty}: ${level.attempts} attempts · ${level.correctAnswers}/${level.questionsAttempted} correct · ${level.accuracyPercent?.let { "$it%" } ?: "No score"}")
                        }
                    }
                    TextButton(onClick = {
                        val token = com.jia.academy.app.auth.SecureSessionStore(context).accessToken()
                        if (token.isNullOrBlank()) { error = "Please sign in again."; return@TextButton }
                        loading = true
                        scope.launch {
                            runCatching { quizRepository.history(BuildConfig.JIA_API_BASE_URL, token) }
                                .onSuccess { history = it }.onFailure { error = it.message ?: "Unable to load history." }
                            loading = false
                        }
                    }) { Text("View my quiz history") }
                    history?.let { rows ->
                        Text("Recent attempts", style = MaterialTheme.typography.titleMedium)
                        if (rows.isEmpty()) Text("No quiz attempts yet.")
                        rows.forEach { row ->
                            Text("${row.difficulty} · ${row.correctCount ?: 0}/${row.questionCount} · ${row.scorePercent?.let { "$it%" } ?: row.status}")
                        }
                    }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
