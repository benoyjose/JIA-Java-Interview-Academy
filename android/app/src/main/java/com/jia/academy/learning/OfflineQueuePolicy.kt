package com.jia.academy.learning

data class PendingActivity(val id: String, val createdAtEpochMs: Long, val attempts: Int, val payloadBytes: Int)

object OfflineQueuePolicy {
    fun eligible(items: List<PendingActivity>, nowEpochMs: Long, maxAgeMs: Long, maxPayloadBytes: Int): List<PendingActivity> {
        require(maxAgeMs >= 0 && maxPayloadBytes > 0)
        return items.filter { it.id.isNotBlank() && it.createdAtEpochMs <= nowEpochMs &&
            nowEpochMs - it.createdAtEpochMs <= maxAgeMs && it.attempts in 0..8 &&
            it.payloadBytes in 1..maxPayloadBytes }
            .distinctBy { it.id }.sortedBy { it.createdAtEpochMs }
    }
}
