package com.jia.academy.app.learning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OfflineReleaseManifestTest {
    private fun validItem(slug: String = "java-basics", checksum: String = "a".repeat(64)) =
        JSONObject().put("slug", slug).put("contentRevision", 3).put("checksum", checksum)

    @Test fun parsesVersionRevisionAndNormalizesChecksum() {
        val manifest = OfflineReleaseManifest.parse(
            JSONObject().put("version", 12).put("items", JSONArray().put(validItem(checksum = "A".repeat(64)))))
        assertEquals(12L, manifest.version)
        assertEquals(3, manifest.items.single().contentRevision)
        assertEquals("a".repeat(64), manifest.items.single().checksum)
    }

    @Test fun rejectsMissingOrNonPositiveVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            OfflineReleaseManifest.parse(JSONObject().put("items", JSONArray()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            OfflineReleaseManifest.parse(JSONObject().put("version", 0).put("items", JSONArray()))
        }
    }

    @Test fun rejectsDuplicateOrMalformedSlugs() {
        assertThrows(IllegalArgumentException::class.java) {
            OfflineReleaseManifest.parse(JSONObject().put("version", 1)
                .put("items", JSONArray().put(validItem()).put(validItem())))
        }
        assertThrows(IllegalArgumentException::class.java) {
            OfflineReleaseManifest.parse(JSONObject().put("version", 1)
                .put("items", JSONArray().put(validItem("../unsafe"))))
        }
    }

    @Test fun rejectsInvalidRevisionAndChecksum() {
        val invalidRevision = validItem().put("contentRevision", 0)
        assertThrows(IllegalArgumentException::class.java) {
            OfflineReleaseManifest.parse(JSONObject().put("version", 1).put("items", JSONArray().put(invalidRevision)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            OfflineReleaseManifest.parse(JSONObject().put("version", 1)
                .put("items", JSONArray().put(validItem(checksum = "not-a-digest"))))
        }
    }

    @Test fun rejectsOversizedManifestBeforeDownloading() {
        val items = JSONArray()
        repeat(OfflineReleaseManifest.MAX_ITEMS + 1) { items.put(validItem("lesson-$it")) }
        assertThrows(IllegalArgumentException::class.java) {
            OfflineReleaseManifest.parse(JSONObject().put("version", 1).put("items", items))
        }
    }
}
