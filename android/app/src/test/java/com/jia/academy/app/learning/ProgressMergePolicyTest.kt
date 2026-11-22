package com.jia.academy.app.learning

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressMergePolicyTest {
    @Test fun mergesProgressMonotonicallyAndKeepsPositionWhenAbsent() {
        val merged = ProgressMergePolicy.merge(ProgressSnapshot(5, 60.0, "{\"position\":2}", 100), 3, 45.0, null, 90)
        assertEquals(5, merged.contentRevision)
        assertEquals(60.0, merged.progressPercent, 0.0)
        assertEquals("{\"position\":2}", merged.lastPositionJson)
        assertEquals(101L, merged.updatedAt)
    }
    @Test fun acceptsNewerProgressAndAdvancesTimestamp() {
        val merged = ProgressMergePolicy.merge(null, 2, 80.0, "{\"position\":8}", 10)
        assertEquals(2, merged.contentRevision)
        assertEquals(80.0, merged.progressPercent, 0.0)
        assertEquals(10L, merged.updatedAt)
    }
    @Test fun preservesCompletedProgressAndStrictlyAdvancesTimestampForRepeatedEvents() {
        val current = ProgressSnapshot(7, 100.0, "{\"section\":4}", 500L)
        val merged = ProgressMergePolicy.merge(current, 6, 10.0, null, 500L)
        assertEquals(7, merged.contentRevision)
        assertEquals(100.0, merged.progressPercent, 0.0)
        assertEquals("{\"section\":4}", merged.lastPositionJson)
        assertEquals(501L, merged.updatedAt)
    }


    @Test fun staleRevisionCannotReplacePositionFromNewerCachedContent() {
        val current = ProgressSnapshot(9, 72.0, "{\"section\":9}", 900L)
        val merged = ProgressMergePolicy.merge(current, 8, 25.0, "{\"section\":2}", 901L)
        assertEquals(9, merged.contentRevision)
        assertEquals(72.0, merged.progressPercent, 0.0)
        assertEquals("{\"section\":9}", merged.lastPositionJson)
    }
}
