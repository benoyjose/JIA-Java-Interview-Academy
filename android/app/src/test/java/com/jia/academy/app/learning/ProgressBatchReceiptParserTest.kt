package com.jia.academy.app.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProgressBatchReceiptParserTest {
    @Test
    fun acknowledgesAcceptedAndDuplicateReceiptsByExactEventId() {
        val receipts = ProgressBatchReceiptParser.parse(
            """{"acceptedCount":1,"duplicateCount":1,"events":[
              {"eventId":"e-1","status":"ACCEPTED","occurredAt":"2026-11-23T10:00:00Z"},
              {"eventId":"e-2","status":"DUPLICATE","occurredAt":"2026-11-23T10:00:01Z"}]}""",
            listOf("e-1", "e-2")
        )
        assertEquals(listOf("e-1", "e-2"), receipts.map { it.eventId })
        assertEquals(ProgressReceiptDisposition.ACCEPTED, receipts[0].disposition)
        assertEquals(ProgressReceiptDisposition.DUPLICATE, receipts[1].disposition)
    }

    @Test
    fun rejectsMissingReceiptRatherThanDroppingQueuedEvent() {
        assertThrows(IllegalArgumentException::class.java) {
            ProgressBatchReceiptParser.parse(
                """{"acceptedCount":1,"duplicateCount":0,"events":[{"eventId":"e-1","status":"ACCEPTED","occurredAt":"now"}]}""",
                listOf("e-1", "e-2")
            )
        }
    }

    @Test
    fun rejectsUnknownDuplicateAndRejectedReceiptIds() {
        val body = """{"acceptedCount":2,"duplicateCount":0,"events":[
          {"eventId":"e-1","status":"ACCEPTED","occurredAt":"now"},
          {"eventId":"not-submitted","status":"ACCEPTED","occurredAt":"now"}]}"""
        assertThrows(IllegalArgumentException::class.java) {
            ProgressBatchReceiptParser.parse(body, listOf("e-1", "e-2"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProgressBatchReceiptParser.parse(
                """{"acceptedCount":0,"duplicateCount":2,"events":[
                  {"eventId":"e-1","status":"DUPLICATE","occurredAt":"now"},
                  {"eventId":"e-1","status":"DUPLICATE","occurredAt":"now"}]}""",
                listOf("e-1", "e-2")
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProgressBatchReceiptParser.parse(
                """{"acceptedCount":0,"duplicateCount":0,"events":[{"eventId":"e-1","status":"REJECTED","occurredAt":"now"}]}""",
                listOf("e-1")
            )
        }
    }

    @Test
    fun enforcesBatchLimitAndUniqueSubmittedIds() {
        assertThrows(IllegalArgumentException::class.java) {
            ProgressBatchReceiptParser.parse("{}", emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProgressBatchReceiptParser.parse("{}", listOf("same", "same"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProgressBatchReceiptParser.parse("{}", (1..101).map { "e-$it" })
        }
    }

    @Test
    fun rejectsReceiptCountSummaryDrift() {
        assertThrows(IllegalArgumentException::class.java) {
            ProgressBatchReceiptParser.parse(
                """{"acceptedCount":0,"duplicateCount":1,"events":[{"eventId":"e-1","status":"ACCEPTED","occurredAt":"now"}]}""",
                listOf("e-1")
            )
        }
    }
}
