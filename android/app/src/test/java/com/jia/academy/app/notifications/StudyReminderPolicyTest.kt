package com.jia.academy.app.notifications

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class StudyReminderPolicyTest {
    @Test fun disabledReminderReturnsNoSchedule() {
        assertNull(StudyReminderPolicy.nextOccurrence(StudyReminderPolicy.Preferences(false, LocalTime.NOON, null, null), LocalDateTime.now(), ZoneId.of("UTC")))
    }

    @Test fun pastTimeRollsToNextDayAndQuietHoursAreRespected() {
        val p = StudyReminderPolicy.Preferences(true, LocalTime.of(8, 0), LocalTime.of(22, 0), LocalTime.of(7, 0))
        val next = StudyReminderPolicy.nextOccurrence(p, LocalDateTime.of(2026, 11, 24, 9, 0), ZoneId.of("Asia/Kolkata"))
        assertEquals(LocalDateTime.of(2026, 11, 25, 8, 0), next)
    }

    @Test fun daytimeQuietWindowMovesReminderToItsEndSameDay() {
        val p = StudyReminderPolicy.Preferences(true, LocalTime.of(19, 0), LocalTime.of(18, 0), LocalTime.of(20, 0))
        val next = StudyReminderPolicy.nextOccurrence(p, LocalDateTime.of(2026, 11, 25, 16, 0), ZoneId.of("Asia/Kolkata"))
        assertEquals(LocalDateTime.of(2026, 11, 25, 20, 0), next)
    }

    @Test fun overnightQuietWindowMovesEarlyReminderToMorningBoundary() {
        val p = StudyReminderPolicy.Preferences(true, LocalTime.of(7, 0), LocalTime.of(22, 0), LocalTime.of(8, 0))
        val next = StudyReminderPolicy.nextOccurrence(p, LocalDateTime.of(2026, 11, 25, 6, 30), ZoneId.of("Asia/Kolkata"))
        assertEquals(LocalDateTime.of(2026, 11, 25, 8, 0), next)
    }

    @Test fun reminderAfterQuietStartMovesToFollowingMorning() {
        val p = StudyReminderPolicy.Preferences(true, LocalTime.of(7, 0), LocalTime.of(22, 0), LocalTime.of(8, 0))
        val next = StudyReminderPolicy.nextOccurrence(p, LocalDateTime.of(2026, 11, 25, 9, 0), ZoneId.of("Asia/Kolkata"))
        assertEquals(LocalDateTime.of(2026, 11, 26, 8, 0), next)
    }
}
