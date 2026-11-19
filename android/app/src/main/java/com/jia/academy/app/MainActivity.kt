package com.jia.academy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Technology(val id: String, val slug: String, val name: String, val description: String?)
data class Category(val id: String, val slug: String, val name: String, val technologySlug: String)
data class LessonSummary(val id: String, val slug: String, val title: String, val summary: String?, val difficulty: String)
data class QuizAvailability(val difficulty: String, val availableQuestionCount: Int)

data class LessonDetail(
    val id: String,
    val slug: String,
    val title: String,
    val summary: String?,
    val difficulty: String,
    val sourceType: String,
    val contentJson: String?
)

class CatalogueRepository {
    private fun apiUrl(baseUrl: String, path: String): String {
        require(baseUrl.isNotBlank()) {
            "Configure JIA_API_BASE_URL in ~/.gradle/gradle.properties to connect to the JIA API."
        }
        return baseUrl.trimEnd('/') + "/api/v1" + path
    }

    private suspend fun get(baseUrl: String, path: String): String = withContext(Dispatchers.IO) {
        val connection = (URL(apiUrl(baseUrl, path)).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) error("JIA API returned HTTP $status")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun segment(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())

    suspend fun getTechnologies(baseUrl: String): List<Technology> {
        val json = JSONArray(get(baseUrl, "/technologies"))
        return (0 until json.length()).map { i ->
            val item = json.getJSONObject(i)
            Technology(item.getString("id"), item.getString("slug"), item.getString("name"),
                item.optString("description").takeIf(String::isNotBlank))
        }
    }

    suspend fun getCategories(baseUrl: String, technologySlug: String): List<Category> {
        val json = JSONArray(get(baseUrl, "/technologies/${segment(technologySlug)}/categories"))
        return (0 until json.length()).map { i ->
            val item = json.getJSONObject(i)
            Category(item.getString("id"), item.getString("slug"), item.getString("name"),
                item.optString("technologySlug", technologySlug))
        }
    }

    suspend fun getLessons(baseUrl: String, categorySlug: String): List<LessonSummary> {
        val json = JSONArray(get(baseUrl, "/categories/${segment(categorySlug)}/lessons"))
        return (0 until json.length()).map { i ->
            val item = json.getJSONObject(i)
            LessonSummary(item.getString("id"), item.getString("slug"), item.getString("title"),
                item.optString("summary").takeIf(String::isNotBlank),
                item.optString("difficulty", "Not specified"))
        }
    }

    suspend fun getQuizAvailability(baseUrl: String, difficulty: String): QuizAvailability {
        val query = URLEncoder.encode(difficulty, Charsets.UTF_8.name())
        val item = JSONObject(get(baseUrl, "/quizzes/availability?difficulty=$query"))
        return QuizAvailability(item.getString("difficulty"), item.getInt("availableQuestionCount"))
    }

    suspend fun getLesson(baseUrl: String, slug: String): LessonDetail {
        val item = JSONObject(get(baseUrl, "/lessons/${segment(slug)}"))
        return LessonDetail(item.getString("id"), item.getString("slug"), item.getString("title"),
            item.optString("summary").takeIf(String::isNotBlank),
            item.optString("difficulty", "Not specified"),
            item.optString("sourceType", "Unknown"),
            item.optString("contentJson").takeIf(String::isNotBlank))
    }
}

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
    val repository = remember { CatalogueRepository() }
    var route by remember { mutableStateOf<BrowseRoute>(BrowseRoute.Home) }
    var backStack by remember { mutableStateOf(listOf<BrowseRoute>()) }
    var signedInAccount by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val authRepository = remember { AuthRepository() }
    LaunchedEffect(Unit) {
        val token = com.jia.academy.app.auth.SecureSessionStore(context).accessToken()
        if (!token.isNullOrBlank()) {
            runCatching { authRepository.currentUser(BuildConfig.JIA_API_BASE_URL, token) }
                .onSuccess { signedInAccount = it.name }
                .onFailure { com.jia.academy.app.auth.SecureSessionStore(context).clear() }
        }
    }
    fun navigate(next: BrowseRoute) {
        backStack = backStack + route
        route = next
    }
    fun goBack() {
        if (backStack.isNotEmpty()) {
            route = backStack.last()
            backStack = backStack.dropLast(1)
        } else route = BrowseRoute.Home
    }
    BackHandler(enabled = route !is BrowseRoute.Home) { goBack() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("JIA") },
            navigationIcon = {
                if (route !is BrowseRoute.Home) TextButton(onClick = ::goBack) { Text("Back") }
            }
        )
    }) { padding ->
        when (val current = route) {
            BrowseRoute.Home -> TechnologyScreen(repository, signedInAccount, Modifier.padding(padding), ::navigate, onSignOut = { signedInAccount = null })
            BrowseRoute.QuizSelection -> QuizSelectionScreen(repository, Modifier.padding(padding))
            BrowseRoute.Authentication -> AuthScreen(AuthRepository(), BuildConfig.JIA_API_BASE_URL, onAuthenticated = { session: JiaSession ->
                signedInAccount = session.account.name
                goBack()
            }, modifier = Modifier.padding(padding))
            is BrowseRoute.Categories -> CategoryScreen(repository, current.technology, Modifier.padding(padding), ::navigate)
            is BrowseRoute.Lessons -> LessonListScreen(repository, current.category, current.technology, Modifier.padding(padding), ::navigate)
            is BrowseRoute.Lesson -> LessonScreen(repository, current.slug, current.title, Modifier.padding(padding))
        }
    }
}

@Composable
private fun TechnologyScreen(
    repository: CatalogueRepository,
    signedInAccount: String?,
    modifier: Modifier,
    navigate: (BrowseRoute) -> Unit,
    onSignOut: () -> Unit
) {
    var items by remember { mutableStateOf<List<Technology>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        runCatching { repository.getTechnologies(BuildConfig.JIA_API_BASE_URL) }
            .onSuccess { items = it }.onFailure { error = it.message ?: "Unable to load Java topics." }
        loading = false
    }
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
            loading -> CircularProgressIndicator()
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
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
    repository: CatalogueRepository,
    technology: Technology,
    modifier: Modifier,
    navigate: (BrowseRoute) -> Unit
) {
    var items by remember(technology.slug) { mutableStateOf<List<Category>>(emptyList()) }
    var loading by remember(technology.slug) { mutableStateOf(true) }
    var error by remember(technology.slug) { mutableStateOf<String?>(null) }
    LaunchedEffect(technology.slug) {
        runCatching { repository.getCategories(BuildConfig.JIA_API_BASE_URL, technology.slug) }
            .onSuccess { items = it }.onFailure { error = it.message ?: "Unable to load categories." }
        loading = false
    }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(technology.name, style = MaterialTheme.typography.headlineSmall)
        Text("Choose a category")
        when {
            loading -> CircularProgressIndicator()
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
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
    repository: CatalogueRepository,
    category: Category,
    technology: Technology,
    modifier: Modifier,
    navigate: (BrowseRoute) -> Unit
) {
    var items by remember(category.slug) { mutableStateOf<List<LessonSummary>>(emptyList()) }
    var loading by remember(category.slug) { mutableStateOf(true) }
    var error by remember(category.slug) { mutableStateOf<String?>(null) }
    LaunchedEffect(category.slug) {
        runCatching { repository.getLessons(BuildConfig.JIA_API_BASE_URL, category.slug) }
            .onSuccess { items = it }.onFailure { error = it.message ?: "Unable to load lessons." }
        loading = false
    }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(technology.name, style = MaterialTheme.typography.labelLarge)
        Text(category.name, style = MaterialTheme.typography.headlineSmall)
        when {
            loading -> CircularProgressIndicator()
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
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
    var lesson by remember(slug) { mutableStateOf<LessonDetail?>(null) }
    var loading by remember(slug) { mutableStateOf(true) }
    var error by remember(slug) { mutableStateOf<String?>(null) }
    LaunchedEffect(slug) {
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
                Spacer(Modifier.padding(top = 4.dp))
                Text("Lesson content", style = MaterialTheme.typography.titleLarge)
                val raw = lesson!!.contentJson
                if (raw.isNullOrBlank() || raw == "null") {
                    Text("This lesson has no published content yet.")
                } else {
                    Text(raw, style = MaterialTheme.typography.bodyMedium)
                }
            }
            else -> Text(fallbackTitle)
        }
    }
}


@Composable
private fun QuizSelectionScreen(repository: CatalogueRepository, modifier: Modifier) {
    val levels = listOf(
        "BEGINNER" to "Beginner",
        "NOVICE" to "Novice",
        "PROFESSIONAL" to "Professional",
        "EXPERT" to "Expert"
    )
    var selected by remember { mutableStateOf("BEGINNER") }
    var availability by remember { mutableStateOf<QuizAvailability?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selected) {
        loading = true
        error = null
        runCatching { repository.getQuizAvailability(BuildConfig.JIA_API_BASE_URL, selected) }
            .onSuccess { availability = it }
            .onFailure { error = it.message ?: "Unable to check quiz availability." }
        loading = false
    }

    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Choose your difficulty", style = MaterialTheme.typography.headlineSmall)
        Text("Select any level. You can practise higher or lower levels whenever you want.")
        levels.forEach { (value, label) ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = selected == value, onClick = { selected = value }, label = { Text(label) })
                    Text(
                        when (value) {
                            "BEGINNER" -> "Fundamentals, definitions and direct examples"
                            "NOVICE" -> "Practical application and common code patterns"
                            "PROFESSIONAL" -> "Advanced Java, Spring and problem-solving"
                            else -> "Architecture, JVM internals and distributed systems"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        Text("Question availability", style = MaterialTheme.typography.titleMedium)
        when {
            loading -> CircularProgressIndicator()
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
            availability != null -> Text("${availability!!.availableQuestionCount} published questions available at ${levels.first { it.first == selected }.second} level.")
        }
        Text(
            "Quiz attempts and personal performance history require sign-in. Questions appear here as reviewed question-bank items are published.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
