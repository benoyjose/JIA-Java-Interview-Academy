package com.jia.academy.app.notifications

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Pure reminder policy. Platform scheduling/permission adapters consume this result. */
object StudyReminderPolicy {
    data class Preferences(val enabled: Boolean, val localTime: LocalTime, val quietStart: LocalTime?, val quietEnd: LocalTime?)

    /**
     * Returns the next local reminder time, moving a reminder to the end of the quiet window
     * rather than skipping an entire day. The supplied zone resolves DST gaps/overlaps.
     */
    fun nextOccurrence(preferences: Preferences, now: LocalDateTime, zone: ZoneId): LocalDateTime? {
        if (!preferences.enabled) return null
        var candidate = now.toLocalDate().atTime(preferences.localTime)
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)

        val start = preferences.quietStart
        val end = preferences.quietEnd
        if (start != null && end != null && start != end && isQuiet(candidate.toLocalTime(), start, end)) {
            val quietEndDate = when {
                start < end -> candidate.toLocalDate()
                candidate.toLocalTime() >= start -> candidate.toLocalDate().plusDays(1)
                else -> candidate.toLocalDate()
            }
            candidate = quietEndDate.atTime(end)
            // A malformed or edge-case timezone transition must never return a past instant.
            if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
        }

        return candidate.atZone(zone).toLocalDateTime()
    }

    private fun isQuiet(time: LocalTime, start: LocalTime, end: LocalTime): Boolean =
        if (start < end) time >= start && time < end else time >= start || time < end
}
