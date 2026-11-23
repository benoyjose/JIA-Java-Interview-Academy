package com.jia.academy.app.lesson

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jia.academy.app.BuildConfig
import com.jia.academy.app.catalogue.CatalogueRepository
import com.jia.academy.app.learning.OfflineLearningStore

@Composable
fun LessonScreen(
    repository: CatalogueRepository,
    slug: String,
    fallbackTitle: String,
    modifier: Modifier
) {
    val context = LocalContext.current
    val lessonViewModel: com.jia.academy.app.lesson.LessonViewModel = viewModel(
        key = slug, factory = com.jia.academy.app.lesson.LessonViewModelFactory(repository)
    )
    val lessonState by lessonViewModel.state.collectAsStateWithLifecycle()
    val lesson = lessonState.lesson
    val loading = lessonState.loading
    val error = lessonState.error
    var progressMessage by remember(slug) { mutableStateOf<String?>(null) }
    var bookmarked by remember(slug) { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    var readingPositionRestored by remember(slug) { mutableStateOf(false) }
    var syncDiagnostics by remember { mutableStateOf<com.jia.academy.app.learning.OfflineSyncDiagnostics?>(null) }
    LaunchedEffect(slug) {
        bookmarked = OfflineLearningStore(context).isBookmarked(slug)
        syncDiagnostics = OfflineLearningStore(context).syncDiagnostics()
        lessonViewModel.load(BuildConfig.JIA_API_BASE_URL, slug)
    }
    LaunchedEffect(lesson?.slug) {
        if (lesson?.slug == slug && !readingPositionRestored) {
            scrollState.scrollTo(OfflineLearningStore(context).readingOffset(slug))
            readingPositionRestored = true
        }
    }
    LaunchedEffect(slug, lesson?.contentRevision, readingPositionRestored) {
        val currentLesson = lesson ?: return@LaunchedEffect
        if (!readingPositionRestored) return@LaunchedEffect
        snapshotFlow { scrollState.value }
            .distinctUntilChanged()
            .debounce(900)
            .collect { offset ->
                val store = OfflineLearningStore(context)
                store.saveReadingOffset(slug, offset)
                if (!com.jia.academy.app.auth.SecureSessionStore(context).accessToken().isNullOrBlank()) {
                    val position = LessonReadingPositionCodec.encode(offset, currentLesson.contentRevision)
                    store.enqueueProgressAndSchedule(
                        context, BuildConfig.JIA_API_BASE_URL, slug,
                        currentLesson.contentRevision, 0.0, position
                    )
                }
            }
    }
    Column(modifier.fillMaxSize().verticalScroll(scrollState).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier.semantics { contentDescription = "Loading lesson" }
            )
            error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(error ?: "Unable to load lesson.", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { lessonViewModel.load(BuildConfig.JIA_API_BASE_URL, slug, force = true) }) { Text("Retry") }
            }
            lesson != null -> {
                Text(lesson!!.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
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
                    }) { Text(if (bookmarked) "★ Saved offline" else "☆ Bookmark", modifier = Modifier.semantics { contentDescription = if (bookmarked) "Remove lesson bookmark" else "Bookmark lesson" }) }
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
                progressMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
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
