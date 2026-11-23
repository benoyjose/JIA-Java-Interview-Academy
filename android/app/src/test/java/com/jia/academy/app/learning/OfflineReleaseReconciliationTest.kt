package com.jia.academy.app.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineReleaseReconciliationTest {
    @Test fun onlyContentOutsideCurrentPublishedManifestIsPruned() {
        assertEquals(setOf("unpublished", "removed"), OfflineReleaseReconciliation.staleCachedSlugs(
            cachedSlugs = setOf("published", "unpublished", "removed"),
            publishedSlugs = setOf("published", "new")
        ))
    }

    @Test fun pruningRequiresCompletedDownloadAndValidatedManifest() {
        assertFalse(OfflineReleaseReconciliation.shouldPruneAfterDownload(false, true))
        assertFalse(OfflineReleaseReconciliation.shouldPruneAfterDownload(true, false))
        assertTrue(OfflineReleaseReconciliation.shouldPruneAfterDownload(true, true))
    }
}
