package com.jia.academy.app

import com.jia.academy.app.catalogue.*
import com.jia.academy.app.learning.OfflineLearningStore
import com.jia.academy.app.lesson.LessonScreen
import com.jia.academy.app.navigation.BrowseRoute

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import com.jia.academy.app.auth.AuthRepository
import com.jia.academy.app.auth.AuthScreen
import com.jia.academy.app.auth.JiaSession
import com.jia.academy.app.assessment.QuizRepository
import com.jia.academy.app.assessment.QuizSelectionScreen
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
import androidx.compose.ui.Alignment
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
    val offlineStore = remember(context) { OfflineLearningStore(context) }
    var allowMeteredDownloads by remember(context) {
        mutableStateOf(offlineStore.downloadsOnMeteredNetwork())
    }
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Allow lesson downloads on mobile data", style = MaterialTheme.typography.bodyMedium)
                Text("Off by default. Progress and bookmarks still sync when connected.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = allowMeteredDownloads, onCheckedChange = { enabled ->
                allowMeteredDownloads = enabled
                offlineStore.setDownloadsOnMeteredNetwork(enabled)
            })
        }
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
