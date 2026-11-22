package com.jia.academy.app.lesson

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LessonReadingPositionCodecTest {
    @Test
    fun roundTripsVersionedReadingOffset() {
        val encoded = LessonReadingPositionCodec.encode(offsetPx = 1420, contentRevision = 3)
        assertEquals(1420, LessonReadingPositionCodec.decodeOffset(encoded))
    }

    @Test
    fun invalidOrUnsupportedAnchorsAreIgnoredSafely() {
        assertNull(LessonReadingPositionCodec.decodeOffset(null))
        assertNull(LessonReadingPositionCodec.decodeOffset("not-json"))
        assertNull(LessonReadingPositionCodec.decodeOffset("""{"version":99,"scrollOffsetPx":10}"""))
        assertNull(LessonReadingPositionCodec.decodeOffset("""{"version":1,"scrollOffsetPx":-1}"""))
    }

    @Test
    fun encodingRejectsNegativeOffsetAndInvalidRevision() {
        runCatching { LessonReadingPositionCodec.encode(-1, 1) }.onSuccess { error("negative offset accepted") }
        runCatching { LessonReadingPositionCodec.encode(1, 0) }.onSuccess { error("invalid revision accepted") }
    }
}
