package com.jia.academy.app.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineQueueCapacityPolicyTest {
    @Test fun permitsNewItemBelowLimit() {
        assertFalse(OfflineQueueCapacityPolicy.shouldReject(false, 9, 10))
    }

    @Test fun rejectsNewItemAtLimit() {
        assertTrue(OfflineQueueCapacityPolicy.shouldReject(false, 10, 10))
    }

    @Test fun permitsCoalescingExistingItemAtLimit() {
        assertFalse(OfflineQueueCapacityPolicy.shouldReject(true, 10, 10))
    }

    @Test fun permitsExistingItemEvenIfQueueAlreadyOverLimit() {
        assertFalse(OfflineQueueCapacityPolicy.shouldReject(true, 12, 10))
    }

    @Test fun exposesActionableNonDestructiveMessage() {
        val message = OfflineQueueCapacityPolicy.rejectionMessage(2_000)
        assertTrue(message.contains("Connect to the internet"))
        assertTrue(message.contains("preserved"))
        assertEquals(OfflineQueueCapacityPolicy.DEFAULT_MAX_PENDING_ITEMS, 2_000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidConfiguredLimit() {
        OfflineQueueCapacityPolicy.shouldReject(false, 0, 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeCount() {
        OfflineQueueCapacityPolicy.shouldReject(false, -1, 100)
    }
}
