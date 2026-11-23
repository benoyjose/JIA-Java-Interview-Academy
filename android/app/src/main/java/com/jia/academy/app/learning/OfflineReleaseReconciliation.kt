package com.jia.academy.app.learning

/**
 * Reconciliation decision for release-managed offline content.
 * Pending progress/bookmarks are stored in separate tables and are never part of this eviction set.
 */
internal object OfflineReleaseReconciliation {
    fun staleCachedSlugs(cachedSlugs: Set<String>, publishedSlugs: Set<String>): Set<String> =
        cachedSlugs - publishedSlugs

    fun shouldPruneAfterDownload(downloadCompleted: Boolean, manifestValidated: Boolean): Boolean =
        downloadCompleted && manifestValidated
}
