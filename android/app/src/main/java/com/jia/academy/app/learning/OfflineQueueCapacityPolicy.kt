package com.jia.academy.app.learning

/**
 * Admission policy for the durable, coalesced learner sync queue.
 *
 * Existing rows are always allowed to be updated (coalescing progress/bookmark state).
 * A new distinct key is rejected at the configured limit; the caller's database
 * transaction must roll back so neither local state nor pending state is partially saved.
 */
internal object OfflineQueueCapacityPolicy {
    const val DEFAULT_MAX_PENDING_ITEMS = 2_000
    const val MIN_MAX_PENDING_ITEMS = 100
    const val MAX_MAX_PENDING_ITEMS = 100_000

    fun shouldReject(existingItem: Boolean, currentPendingItems: Int, maximum: Int): Boolean {
        require(currentPendingItems >= 0) { "Pending queue count cannot be negative" }
        require(maximum in MIN_MAX_PENDING_ITEMS..MAX_MAX_PENDING_ITEMS) {
            "Pending queue limit must be between $MIN_MAX_PENDING_ITEMS and $MAX_MAX_PENDING_ITEMS"
        }
        return !existingItem && currentPendingItems >= maximum
    }

    fun rejectionMessage(maximum: Int): String =
        "Offline activity queue is full ($maximum items). Connect to the internet and sync before recording activity for another lesson. Your queued activity has been preserved."
}
