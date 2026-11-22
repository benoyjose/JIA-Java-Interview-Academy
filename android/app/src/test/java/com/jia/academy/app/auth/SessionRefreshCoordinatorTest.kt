package com.jia.academy.app.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SessionRefreshCoordinatorTest {
    @Test
    fun simultaneousExpiredAccessRequestsShareOneRefreshAndReceiveRotatedToken() = runBlocking {
        val coordinator = SessionRefreshCoordinator()
        val refreshCount = AtomicInteger()
        var persistedAccessToken = "expired-token"

        val results = (1..8).map {
            async(Dispatchers.Default) {
                coordinator.refreshIfUnchanged(
                    rejectedAccessToken = "expired-token",
                    currentAccessToken = { persistedAccessToken },
                    refreshAndPersist = {
                        delay(40)
                        refreshCount.incrementAndGet()
                        persistedAccessToken = "rotated-token"
                        "rotated-token"
                    }
                )
            }
        }.awaitAll()

        assertEquals(1, refreshCount.get())
        assertEquals(setOf("rotated-token"), results.toSet())
    }

    @Test
    fun skipsRefreshWhenAnotherRequestAlreadyPersistedNewAccessToken() = runBlocking {
        val coordinator = SessionRefreshCoordinator()
        var calls = 0
        val token = coordinator.refreshIfUnchanged(
            rejectedAccessToken = "old",
            currentAccessToken = { "already-rotated" },
            refreshAndPersist = { calls++; "unexpected" }
        )
        assertEquals("already-rotated", token)
        assertEquals(0, calls)
    }
}
