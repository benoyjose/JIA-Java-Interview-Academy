package com.jia.academy.app.learning

import org.json.JSONObject

internal data class OfflineReleaseItem(
    val slug: String,
    val contentRevision: Int,
    val checksum: String,
    /** Optional for compatibility with existing server manifests. */
    val byteSize: Long? = null
)

internal data class OfflineReleaseManifest(
    val version: Long,
    val items: List<OfflineReleaseItem>
) {
    companion object {
        const val MAX_ITEMS = 1_000
        const val MAX_ITEM_BYTES = 5L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 50L * 1024 * 1024
        private val SLUG_PATTERN = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
        private val SHA256_PATTERN = Regex("[a-fA-F0-9]{64}")

        fun parse(json: JSONObject): OfflineReleaseManifest {
            val version = json.optLong("version", 0L)
            require(version > 0L) { "Content manifest version must be positive" }
            val array = json.optJSONArray("items")
                ?: throw IllegalArgumentException("Content manifest items are missing")
            require(array.length() <= MAX_ITEMS) { "Content manifest exceeds the offline item limit" }

            val seen = HashSet<String>(array.length())
            var declaredTotalBytes = 0L
            val items = buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index)
                        ?: throw IllegalArgumentException("Manifest item $index is invalid")
                    val slug = item.optString("slug").trim()
                    val revision = item.optInt("contentRevision", 0)
                    val checksum = item.optString("checksum").trim()
                    val byteSize = if (item.has("byteSize") && !item.isNull("byteSize")) {
                        item.optLong("byteSize", -1L)
                    } else null
                    require(SLUG_PATTERN.matches(slug)) { "Manifest item $index has an invalid lesson slug" }
                    require(seen.add(slug)) { "Content manifest contains duplicate lesson slugs" }
                    require(revision > 0) { "Manifest item $slug has an invalid content revision" }
                    require(SHA256_PATTERN.matches(checksum)) { "Manifest item $slug has an invalid SHA-256 checksum" }
                    if (byteSize != null) {
                        require(byteSize in 1..MAX_ITEM_BYTES) { "Manifest item $slug exceeds the offline item size limit" }
                        declaredTotalBytes = Math.addExact(declaredTotalBytes, byteSize)
                        require(declaredTotalBytes <= MAX_TOTAL_BYTES) { "Manifest exceeds the offline storage budget" }
                    }
                    add(OfflineReleaseItem(slug, revision, checksum.lowercase(), byteSize))
                }
            }
            return OfflineReleaseManifest(version, items)
        }
    }
}
