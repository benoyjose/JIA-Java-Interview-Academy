package com.jia.academy.app.learning

import com.jia.academy.app.catalogue.LessonDetail
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.charset.StandardCharsets

/** Observable outcome for a manifest-driven offline content refresh. */
data class OfflineDownloadProgress(
    val manifestVersion: Long,
    val completedItems: Int,
    val totalItems: Int,
    val currentSlug: String?
)

data class OfflineDownloadSummary(
    val manifestVersion: Long,
    val downloadedCount: Int,
    val alreadyCurrentCount: Int
)

/**
 * Downloads a validated release manifest sequentially. The caller owns the coroutine Job and can
 * cancel it at any time; cancellation is checked before each item and is never swallowed.
 * Lesson bytes are validated before cacheLesson, preserving the last-known-good revision on error.
 */
internal class OfflineManifestDownloadCoordinator(
    private val store: OfflineLearningStore,
    private val loadLesson: suspend (slug: String) -> LessonDetail
) {
    suspend fun download(
        manifest: OfflineReleaseManifest,
        onProgress: (OfflineDownloadProgress) -> Unit = {}
    ): OfflineDownloadSummary {
        store.requireManifestVersionNotOlder(manifest.version)
        var downloaded = 0
        var current = 0
        manifest.items.forEachIndexed { index, item ->
            currentCoroutineContext().ensureActive()
            onProgress(OfflineDownloadProgress(manifest.version, index, manifest.items.size, item.slug))
            val cached = store.cachedLesson(item.slug)
            if (cached != null && cached.contentRevision == item.contentRevision &&
                store.hasCachedLesson(item.slug, item.checksum)) {
                current++
            } else {
                val lesson = loadLesson(item.slug)
                require(lesson.slug == item.slug) { "Downloaded lesson slug does not match manifest" }
                require(lesson.contentRevision == item.contentRevision) {
                    "Downloaded lesson revision does not match manifest for ${item.slug}"
                }
                val content = lesson.contentJson
                    ?: throw IllegalArgumentException("Manifest lesson ${item.slug} has no content")
                val actualBytes = content.toByteArray(StandardCharsets.UTF_8).size.toLong()
                require(actualBytes <= OfflineReleaseManifest.MAX_ITEM_BYTES) {
                    "Downloaded lesson ${item.slug} exceeds the offline item size limit"
                }
                item.byteSize?.let { expected ->
                    require(actualBytes == expected) {
                        "Downloaded lesson byte size does not match manifest for ${item.slug}"
                    }
                }
                require(OfflineContentChecksum.sha256(content).equals(item.checksum, ignoreCase = true)) {
                    "Downloaded lesson checksum does not match manifest for ${item.slug}"
                }
                currentCoroutineContext().ensureActive()
                store.cacheLesson(lesson, item.checksum)
                downloaded++
            }
            onProgress(OfflineDownloadProgress(manifest.version, index + 1, manifest.items.size, null))
        }
        // Advance rollback watermark only after every item passed integrity validation.
        store.recordValidatedManifestVersion(manifest.version)
        // Prune only after every manifest item was fetched and integrity-checked.
        if (OfflineReleaseReconciliation.shouldPruneAfterDownload(true, true)) {
            store.reconcileCachedLessons(manifest.items.map { it.slug }.toSet())
        }
        return OfflineDownloadSummary(manifest.version, downloaded, current)
    }
}
