package com.jia.academy.app.learning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

/**
 * Offline library surface. Storage operations are delegated to the caller so the UI can be
 * hosted by the app's navigation and lifecycle-aware state holder.
 */
@Composable
fun OfflineLibraryScreen(
    lessons: List<OfflineCachedLessonInfo>,
    loading: Boolean,
    downloadActive: Boolean,
    downloadProgressLabel: String? = null,
    errorMessage: String? = null,
    onRefresh: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onOpenLesson: (String) -> Unit,
    onRemoveLesson: (String) -> Unit,
    onClearLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    var removeTarget by remember { mutableStateOf<OfflineCachedLessonInfo?>(null) }
    var clearRequested by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Offline library", style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() })
            TextButton(onClick = onRefresh, enabled = !loading) { Text("Refresh") }
        }
        Text("${lessons.size} downloaded lessons",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        OutlinedButton(onClick = onDownload, enabled = !downloadActive,
            modifier = Modifier.fillMaxWidth()) {
            Text(if (lessons.isEmpty()) "Download available lessons" else "Download / update offline library")
        }
        if (downloadActive) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    Text(downloadProgressLabel ?: "Downloading lessons…",
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    OutlinedButton(onClick = onCancelDownload) { Text("Cancel download") }
                }
            }
        }
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
        if (lessons.isEmpty() && !loading) {
            Text("No lessons are saved for offline use yet.")
            Button(onClick = onRefresh) { Text("Check downloads") }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(lessons, key = { it.slug }) { lesson ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(lesson.title, style = MaterialTheme.typography.titleMedium)
                            Text("Revision ${lesson.contentRevision} • ${formatBytes(lesson.contentBytes)}",
                                style = MaterialTheme.typography.bodyMedium)
                            Text("Saved ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(lesson.cachedAt))}",
                                style = MaterialTheme.typography.bodySmall)
                            Text("Verified content • SHA-256",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.semantics { contentDescription = "Integrity verified for ${lesson.title}" })
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onOpenLesson(lesson.slug) }) { Text("Open") }
                                OutlinedButton(onClick = { removeTarget = lesson }) { Text("Remove") }
                            }
                        }
                    }
                }
            }
            if (lessons.isNotEmpty()) {
                OutlinedButton(onClick = { clearRequested = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Clear downloaded lessons")
                }
            }
        }
    }

    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remove downloaded lesson?") },
            text = { Text("${target.title} will be removed from this device. Your pending progress and bookmarks will be kept.") },
            confirmButton = { TextButton(onClick = { onRemoveLesson(target.slug); removeTarget = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Keep") } }
        )
    }
    if (clearRequested) {
        AlertDialog(
            onDismissRequest = { clearRequested = false },
            title = { Text("Clear offline library?") },
            text = { Text("Downloaded lesson content will be deleted. Pending progress, bookmarks and settings will remain.") },
            confirmButton = { TextButton(onClick = { onClearLibrary(); clearRequested = false }) { Text("Clear library") } },
            dismissButton = { TextButton(onClick = { clearRequested = false }) { Text("Cancel") } }
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "${bytes} B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
}
