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

    @Test fun acceptsOptionalDeclaredByteSizeAndKeepsLegacyManifestCompatible() {
        val sized = validItem().put("byteSize", 1024)
        val manifest = OfflineReleaseManifest.parse(JSONObject().put("version", 1).put("items", JSONArray().put(sized)))
        assertEquals(1024L, manifest.items.single().byteSize)
        val legacy = OfflineReleaseManifest.parse(JSONObject().put("version", 1).put("items", JSONArray().put(validItem())))
        assertEquals(null, legacy.items.single().byteSize)
    }

    @Test fun rejectsItemAndAggregateStorageLimits() {
        assertThrows(IllegalArgumentException::class.java) {
            OfflineReleaseManifest.parse(JSONObject().put("version", 1)
                .put("items", JSONArray().put(validItem().put("byteSize", OfflineReleaseManifest.MAX_ITEM_BYTES + 1))))
        }
        val items = JSONArray()
            .put(validItem("lesson-one").put("byteSize", OfflineReleaseManifest.MAX_ITEM_BYTES))
            .put(validItem("lesson-two").put("byteSize", OfflineReleaseManifest.MAX_ITEM_BYTES))
            .put(validItem("lesson-three").put("byteSize", OfflineReleaseManifest.MAX_ITEM_BYTES))
            .put(validItem("lesson-four").put("byteSize", OfflineReleaseManifest.MAX_ITEM_BYTES))
            .put(validItem("lesson-five").put("byteSize", OfflineReleaseManifest.MAX_ITEM_BYTES))
            .put(validItem("lesson-six").put("byteSize", OfflineReleaseManifest.MAX_ITEM_BYTES))
            .put(validItem("lesson-seven").put("byteSize", OfflineReleaseManifest.MAX_ITEM_BYTES))
            .put(validItem("lesson-eight").put("byteSize", OfflineReleaseManifest.MAX_ITEM_BYTES))
            .put(validItem("lesson-nine").put("byteSize", OfflineReleaseManifest.MAX_ITEM_BYTES))
            .put(validItem("lesson-ten").put("byteSize", OfflineReleaseManifest.MAX_ITEM_BYTES))
            .put(validItem("lesson-eleven").put("byteSize", OfflineReleaseManifest.MAX_ITEM_BYTES))
        assertThrows(IllegalArgumentException::class.java) {
            OfflineReleaseManifest.parse(JSONObject().put("version", 1).put("items", items))
        }
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
