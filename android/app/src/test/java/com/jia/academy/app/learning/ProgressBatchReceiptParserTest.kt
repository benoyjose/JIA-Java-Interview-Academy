package com.jia.academy.app.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProgressBatchReceiptParserTest {
    @Test
    fun validatesEverySubmittedEventAndSummaryCounts() {
        val ids = listOf("e-1", "e-2")
        val receipts = ProgressBatchReceiptParser.parse(
            """{"acceptedCount":1,"duplicateCount":1,"events":[
              {"eventId":"e-1","status":"ACCEPTED","occurredAt":"2026-11-23T10:00:00Z"},
              {"eventId":"e-2","status":"DUPLICATE","occurredAt":"2026-11-23T10:00:01Z"}]}""",
            ids
        )
        assertEquals(ids, receipts.map { it.eventId })
        assertEquals(ProgressReceiptDisposition.ACCEPTED, receipts[0].disposition)
        assertEquals(ProgressReceiptDisposition.DUPLICATE, receipts[1].disposition)
    }

    @Test
    fun rejectsMissingUnknownRepeatedOrRejectedReceipts() {
        val malformed = listOf(
            """{"acceptedCount":1,"duplicateCount":0,"events":[{"eventId":"e-1","status":"ACCEPTED","occurredAt":"2026-01-01T00:00:00Z"}]}""",
            """{"acceptedCount":2,"duplicateCount":0,"events":[{"eventId":"e-1","status":"ACCEPTED","occurredAt":"2026-01-01T00:00:00Z"},{"eventId":"unknown","status":"ACCEPTED","occurredAt":"2026-01-01T00:00:00Z"}]}""",
            """{"acceptedCount":0,"duplicateCount":2,"events":[{"eventId":"e-1","status":"DUPLICATE","occurredAt":"2026-01-01T00:00:00Z"},{"eventId":"e-1","status":"DUPLICATE","occurredAt":"2026-01-01T00:00:00Z"}]}""",
            """{"acceptedCount":0,"duplicateCount":0,"events":[{"eventId":"e-1","status":"REJECTED","occurredAt":"2026-01-01T00:00:00Z"},{"eventId":"e-2","status":"ACCEPTED","occurredAt":"2026-01-01T00:00:00Z"}]}"""
        )
        malformed.forEach { body ->
            assertThrows(IllegalArgumentException::class.java) {
                ProgressBatchReceiptParser.parse(body, listOf("e-1", "e-2"))
            }
        }
    }

    @Test
    fun enforcesBatchBoundsUniqueIdsAndValidTimestamp() {
        assertThrows(IllegalArgumentException::class.java) {
            ProgressBatchReceiptParser.parse("{}", emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProgressBatchReceiptParser.parse("{}", listOf("x", "x"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProgressBatchReceiptParser.parse("{}", (1..101).map { "e-$it" })
        }
        assertThrows(Exception::class.java) {
            ProgressBatchReceiptParser.parse(
                """{"acceptedCount":1,"duplicateCount":0,"events":[{"eventId":"x","status":"ACCEPTED","occurredAt":"not-an-instant"}]}""",
                listOf("x")
            )
        }
    }

    @Test
    fun retriesOnlyTransientBatchStatuses() {
        assertEquals(BatchSyncResponseAction.ACKNOWLEDGE, ProgressBatchSyncResponsePolicy.classify(200))
        assertEquals(BatchSyncResponseAction.RETRY, ProgressBatchSyncResponsePolicy.classify(429))
        assertEquals(BatchSyncResponseAction.RETRY, ProgressBatchSyncResponsePolicy.classify(503))
        assertEquals(BatchSyncResponseAction.REBASE, ProgressBatchSyncResponsePolicy.classify(409))
        assertEquals(BatchSyncResponseAction.REJECT, ProgressBatchSyncResponsePolicy.classify(422))
    }
}
