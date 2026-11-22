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
}