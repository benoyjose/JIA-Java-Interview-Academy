package com.jia.academy.app.learning

import org.json.JSONObject

/** Receipt contract for POST /me/progress-events/batch. A queue item is acked only by its event ID. */
internal data class ProgressBatchReceipt(
    val eventId: String,
    val disposition: ProgressReceiptDisposition,
    val occurredAt: String
)

internal enum class ProgressReceiptDisposition { ACCEPTED, DUPLICATE }

/**
 * Parses the server's atomic batch response and verifies it accounts for every submitted ID exactly
 * once. Missing, unknown, repeated, or rejected IDs are not silently acknowledged.
 */
internal object ProgressBatchReceiptParser {
    const val MAX_BATCH_SIZE = 100

    fun parse(responseBody: String, submittedEventIds: List<String>): List<ProgressBatchReceipt> {
        require(submittedEventIds.isNotEmpty() && submittedEventIds.size <= MAX_BATCH_SIZE) {
            "Progress batch must contain between 1 and $MAX_BATCH_SIZE events"
        }
        require(submittedEventIds.all(String::isNotBlank) && submittedEventIds.toSet().size == submittedEventIds.size) {
            "Submitted progress event IDs must be nonblank and unique"
        }

        val root = JSONObject(responseBody)
        val events = root.optJSONArray("events")
            ?: throw IllegalArgumentException("Batch response is missing event receipts")
        if (events.length() != submittedEventIds.size) {
            throw IllegalArgumentException("Batch response does not contain one receipt per submitted event")
        }

        val expected = submittedEventIds.toSet()
        val seen = HashSet<String>()
        val receipts = buildList {
            for (index in 0 until events.length()) {
                val item = events.optJSONObject(index)
                    ?: throw IllegalArgumentException("Batch receipt at index $index is invalid")
                val id = item.optString("eventId").takeIf(String::isNotBlank)
                    ?: throw IllegalArgumentException("Batch receipt is missing eventId")
                require(id in expected) { "Batch response contains an unknown event ID" }
                require(seen.add(id)) { "Batch response repeats an event ID" }
                val disposition = when (item.optString("status")) {
                    "ACCEPTED" -> ProgressReceiptDisposition.ACCEPTED
                    "DUPLICATE" -> ProgressReceiptDisposition.DUPLICATE
                    else -> throw IllegalArgumentException("Batch response contains an unrecognized receipt status")
                }
                val occurredAt = item.optString("occurredAt").takeIf(String::isNotBlank)
                    ?: throw IllegalArgumentException("Batch receipt is missing occurredAt")
                add(ProgressBatchReceipt(id, disposition, occurredAt))
            }
        }
        require(seen == expected) { "Batch response omitted one or more submitted event IDs" }
        val acceptedCount = receipts.count { it.disposition == ProgressReceiptDisposition.ACCEPTED }
        val duplicateCount = receipts.count { it.disposition == ProgressReceiptDisposition.DUPLICATE }
        require(root.optInt("acceptedCount", -1) == acceptedCount &&
            root.optInt("duplicateCount", -1) == duplicateCount) {
            "Batch receipt summary does not match its event receipts"
        }
        return receipts
    }
}
