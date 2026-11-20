package com.jia.academy.app

import com.jia.academy.app.catalogue.*
import com.jia.academy.app.learning.OfflineLearningStore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import com.jia.academy.app.auth.AuthRepository
import com.jia.academy.app.auth.AuthScreen
import com.jia.academy.app.auth.JiaSession
import com.jia.academy.app.assessment.QuizRepository
import com.jia.academy.app.assessment.JiaQuizMode
import com.jia.academy.app.assessment.JiaQuizAttempt
import com.jia.academy.app.assessment.JiaQuizResult
import com.jia.academy.app.assessment.JiaQuizHistoryItem
import com.jia.academy.app.assessment.JiaQuizPerformance
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant

private sealed interface BrowseRoute {
    data object Home : BrowseRoute
    data object QuizSelection : BrowseRoute
    data object Authentication : BrowseRoute
    data class Categories(val technology: Technology) : BrowseRoute
    data class Lessons(val category: Category, val technology: Technology) : BrowseRoute
    data class Lesson(val slug: String, val title: String) : BrowseRoute
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { JiaApp() } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JiaApp() {
    val context = LocalContext.current
    val repository = remember(context) { CatalogueRepository(OfflineLearningStore(context)) }
    val catalogueViewModel: CatalogueViewModel = viewModel(factory = CatalogueViewModelFactory(repository))
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    var selectedTechnology by remember { mutableStateOf<Technology?>(null) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var selectedLesson by remember { mutableStateOf<Pair<String, String>?>(null) }
    var signedInAccount by remember { mutableStateOf<String?>(null) }
    val authRepository = remember { AuthRepository() }

    LaunchedEffect(Unit) {
        val sessionStore = com.jia.academy.app.auth.SecureSessionStore(context)
        val token = sessionStore.accessToken()
        if (!token.isNullOrBlank()) {
            val account = runCatching { authRepository.currentUser(BuildConfig.JIA_API_BASE_URL, token) }
                .getOrElse {
                    val refresh = sessionStore.refreshToken()
                    if (refresh.isNullOrBlank()) {
                        sessionStore.clear()
                        null
                    } else {
                        runCatching { authRepository.refresh(BuildConfig.JIA_API_BASE_URL, refresh) }
                            .onSuccess { session ->
                                val userJson = org.json.JSONObject()
                                    .put("id", session.account.id)
                                    .put("name", session.account.name)
                                    .put("email", session.account.email)
                                    .put("roles", org.json.JSONArray(session.account.roles))
                                    .toString()
                                sessionStore.save(session.accessToken, session.refreshToken, session.expiresAt, userJson)
                            }
                            .getOrNull()?.account
                    }
                }
            signedInAccount = account?.name
        }
    }

    fun navigate(next: BrowseRoute) {
        when (next) {
            BrowseRoute.Home -> navController.navigate("home")
            BrowseRoute.QuizSelection -> navController.navigate("quiz")
            BrowseRoute.Authentication -> navController.navigate("auth")
            is BrowseRoute.Categories -> {
                selectedTechnology = next.technology
                navController.navigate("categories")
            }
            is BrowseRoute.Lessons -> {
                selectedTechnology = next.technology
                selectedCategory = next.category
                navController.navigate("lessons")
            }
            is BrowseRoute.Lesson -> {
                selectedLesson = next.slug to next.title
                navController.navigate("lesson")
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("JIA") },
            navigationIcon = {
                if (currentEntry?.destination?.route != "home") {
                    TextButton(onClick = { navController.popBackStack() }) { Text("Back") }
                }
            }
        )
    }) { padding ->
        NavHost(navController = navController, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") {
                TechnologyScreen(catalogueViewModel, signedInAccount, Modifier.fillMaxSize(), ::navigate,
                    onSignOut = { signedInAccount = null })
            }
            composable("quiz") {
                QuizSelectionScreen(repository, signedInAccount != null, Modifier.fillMaxSize())
            }
            composable("auth") {
                AuthScreen(AuthRepository(), BuildConfig.JIA_API_BASE_URL, onAuthenticated = { session: JiaSession ->
                    signedInAccount = session.account.name
                    navController.popBackStack()
                }, modifier = Modifier.fillMaxSize())
            }
            composable("categories") {
                selectedTechnology?.let { CategoryScreen(catalogueViewModel, it, Modifier.fillMaxSize(), ::navigate) }
            }
            composable("lessons") {
                val technology = selectedTechnology
                val category = selectedCategory
                if (technology != null && category != null) {
                    LessonListScreen(catalogueViewModel, category, technology, Modifier.fillMaxSize(), ::navigate)
                }
            }
            composable("lesson") {
                selectedLesson?.let { (slug, title) -> LessonScreen(repository, slug, title, Modifier.fillMaxSize()) }
            }
        }
    }
}

@Composable
private fun TechnologyScreen(
    catalogueViewModel: CatalogueViewModel,
    signedInAccount: String?,
    modifier: Modifier,
    navigate: (BrowseRoute) -> Unit,
    onSignOut: () -> Unit
) {
    val state by catalogueViewModel.technologies.collectAsStateWithLifecycle()
    val items = state.data.orEmpty()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { catalogueViewModel.loadTechnologies(BuildConfig.JIA_API_BASE_URL) }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Java Interview Academy", style = MaterialTheme.typography.headlineMedium)
        if (signedInAccount == null) {
            Button(onClick = { navigate(BrowseRoute.Authentication) }) { Text("Sign in / Create account") }
        } else {
            Text("Signed in as $signedInAccount", style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = {
                val token = com.jia.academy.app.auth.SecureSessionStore(context).accessToken()
                scope.launch {
                    if (!token.isNullOrBlank()) runCatching {
                        AuthRepository().logout(BuildConfig.JIA_API_BASE_URL, token)
                    }
                    com.jia.academy.app.auth.SecureSessionStore(context).clear()
                    onSignOut()
                }
            }) { Text("Sign out") }
        }
        Text("Quick explanations. Practical code. Interview-ready answers.")
        Button(onClick = { navigate(BrowseRoute.QuizSelection) }) { Text("Choose a quiz difficulty") }
        Text("Explore Java topics", style = MaterialTheme.typography.titleLarge)
        when {
            state.loading -> CircularProgressIndicator()
            state.error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.error ?: "Unable to load topics.", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { catalogueViewModel.loadTechnologies(BuildConfig.JIA_API_BASE_URL, force = true) }) { Text("Retry") }
            }
            items.isEmpty() -> Text("No published topics are available yet.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items, key = { it.id }) { technology ->
                    Card(Modifier.fillMaxWidth().clickable {
                        navigate(BrowseRoute.Categories(technology))
                    }) {
                        Column(Modifier.padding(16.dp)) {
                            Text(technology.name, style = MaterialTheme.typography.titleMedium)
                            technology.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            Text("Browse categories →", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryScreen(
    catalogueViewModel: CatalogueViewModel,
    technology: Technology,
    modifier: Modifier,
    navigate: (BrowseRoute) -> Unit
) {
    val state by catalogueViewModel.categories.collectAsStateWithLifecycle()
    val items = state.data.orEmpty()
    LaunchedEffect(technology.slug) {
        catalogueViewModel.loadCategories(BuildConfig.JIA_API_BASE_URL, technology.slug)
    }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(technology.name, style = MaterialTheme.typography.headlineSmall)
        Text("Choose a category")
        when {
            state.loading -> CircularProgressIndicator()
            state.error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.error ?: "Unable to load categories.", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { catalogueViewModel.loadCategories(BuildConfig.JIA_API_BASE_URL, technology.slug, force = true) }) { Text("Retry") }
            }
            items.isEmpty() -> Text("No published categories are available yet.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items, key = { it.id }) { category ->
                    Card(Modifier.fillMaxWidth().clickable {
                        navigate(BrowseRoute.Lessons(category, technology))
                    }) {
                        Text(category.name, Modifier.padding(18.dp), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonListScreen(
    catalogueViewModel: CatalogueViewModel,
    category: Category,
    technology: Technology,
    modifier: Modifier,
    navigate: (BrowseRoute) -> Unit
) {
    val state by catalogueViewModel.lessons.collectAsStateWithLifecycle()
    val items = state.data.orEmpty()
    LaunchedEffect(category.slug) {
        catalogueViewModel.loadLessons(BuildConfig.JIA_API_BASE_URL, category.slug)
    }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(technology.name, style = MaterialTheme.typography.labelLarge)
        Text(category.name, style = MaterialTheme.typography.headlineSmall)
        when {
            state.loading -> CircularProgressIndicator()
            state.error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.error ?: "Unable to load lessons.", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { catalogueViewModel.loadLessons(BuildConfig.JIA_API_BASE_URL, category.slug, force = true) }) { Text("Retry") }
            }
            items.isEmpty() -> Text("No published lessons are available yet.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items, key = { it.id }) { lesson ->
                    Card(Modifier.fillMaxWidth().clickable {
                        navigate(BrowseRoute.Lesson(lesson.slug, lesson.title))
                    }) {
                        Column(Modifier.padding(16.dp)) {
                            Text(lesson.title, style = MaterialTheme.typography.titleMedium)
                            lesson.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            Text("Difficulty: ${lesson.difficulty}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonScreen(
    repository: CatalogueRepository,
    slug: String,
    fallbackTitle: String,
    modifier: Modifier
) {
    val context = LocalContext.current
    var lesson by remember(slug) { mutableStateOf<LessonDetail?>(null) }
    var loading by remember(slug) { mutableStateOf(true) }
    var error by remember(slug) { mutableStateOf<String?>(null) }
    var progressMessage by remember(slug) { mutableStateOf<String?>(null) }
    var bookmarked by remember(slug) { mutableStateOf(false) }
    var syncDiagnostics by remember { mutableStateOf<com.jia.academy.app.learning.OfflineSyncDiagnostics?>(null) }
    LaunchedEffect(slug) {
        bookmarked = OfflineLearningStore(context).isBookmarked(slug)
        syncDiagnostics = OfflineLearningStore(context).syncDiagnostics()
        runCatching { repository.getLesson(BuildConfig.JIA_API_BASE_URL, slug) }
            .onSuccess { lesson = it }.onFailure { error = it.message ?: "Unable to load lesson." }
        loading = false
    }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            loading -> CircularProgressIndicator()
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
            lesson != null -> {
                Text(lesson!!.title, style = MaterialTheme.typography.headlineSmall)
                Text("Difficulty: ${lesson!!.difficulty} · Source: ${lesson!!.sourceType}",
                    style = MaterialTheme.typography.labelMedium)
                lesson!!.summary?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val token = com.jia.academy.app.auth.SecureSessionStore(context).accessToken()
                        if (token.isNullOrBlank()) {
                            progressMessage = "Sign in to sync bookmarks across devices."
                        } else {
                            OfflineLearningStore(context).setBookmarkAndSchedule(
                                context, BuildConfig.JIA_API_BASE_URL, slug, !bookmarked
                            )
                            bookmarked = !bookmarked
                            progressMessage = if (bookmarked) "Bookmark saved offline; it will sync when connected."
                                else "Bookmark removal queued for synchronization."
                        }
                    }) { Text(if (bookmarked) "★ Saved offline" else "☆ Bookmark") }
                    Button(onClick = {

                    val token = com.jia.academy.app.auth.SecureSessionStore(context).accessToken()
                    if (token.isNullOrBlank()) {
                        progressMessage = "Sign in to save your learning progress."
                    } else {
                        runCatching {
                            OfflineLearningStore(context).enqueueProgressAndSchedule(
                                context, BuildConfig.JIA_API_BASE_URL, slug,
                                lesson!!.contentRevision, 100.0
                            )
                        }.onSuccess {
                            progressMessage = "Completion saved. It will sync when a connection is available."
                        }.onFailure {
                            progressMessage = it.message ?: "Could not save progress."
                        }
                    }
                }) { Text("Mark lesson complete") }
                }
                progressMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                syncDiagnostics?.let { diagnostics ->
                    Text("Offline sync: " + (diagnostics.lastSuccessAt?.let { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)) } ?: "not synchronized yet"),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    diagnostics.lastError?.let { Text("Last sync issue: " + it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
                Spacer(Modifier.padding(top = 4.dp))
                Text("Lesson content", style = MaterialTheme.typography.titleLarge)
                val raw = lesson!!.contentJson
                if (raw.isNullOrBlank() || raw == "null") {
                    Text("This lesson has no published content yet.")
                } else {
                    com.jia.academy.app.lesson.LessonContentRenderer(raw)
                }
            }
            else -> Text(fallbackTitle)
        }
    }
}


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
private fun QuizSelectionScreen(repository: CatalogueRepository, signedIn: Boolean, modifier: Modifier) {
    val levels = listOf(
        "BEGINNER" to "Beginner", "NOVICE" to "Novice",
        "PROFESSIONAL" to "Professional", "EXPERT" to "Expert"
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val quizRepository = remember { QuizRepository() }
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
