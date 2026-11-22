package com.jia.academy.app.auth

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coalesces concurrent refresh attempts within the app process.
 * The current-token recheck is performed inside the mutex, after the previous
 * caller has atomically persisted its rotated session.
 */
internal class SessionRefreshCoordinator {
    private val mutex = Mutex()

    suspend fun refreshIfUnchanged(
        rejectedAccessToken: String,
        currentAccessToken: () -> String?,
        refreshAndPersist: suspend () -> String
    ): String = mutex.withLock {
        val latest = currentAccessToken()
        if (!latest.isNullOrBlank() && latest != rejectedAccessToken) {
            latest
        } else {
            refreshAndPersist()
        }
    }
}

internal object SharedSessionRefreshCoordinator {
    val instance = SessionRefreshCoordinator()
}
