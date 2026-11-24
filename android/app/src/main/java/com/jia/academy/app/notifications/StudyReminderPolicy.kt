package com.jia.academy.app.notifications

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Pure reminder policy. Platform scheduling/permission adapters consume this result. */
object StudyReminderPolicy {
    data class Preferences(val enabled: Boolean, val localTime: LocalTime, val quietStart: LocalTime?, val quietEnd: LocalTime?)
    fun nextOccurrence(preferences: Preferences, now: LocalDateTime, zone: ZoneId): LocalDateTime? {
        if (!preferences.enabled) return null
        var date = now.toLocalDate()
        var candidate = date.atTime(preferences.localTime)
        if (!candidate.isAfter(now)) { date = date.plusDays(1); candidate = date.atTime(preferences.localTime) }
        if (isQuiet(candidate.toLocalTime(), preferences.quietStart, preferences.quietEnd)) {
            date = date.plusDays(1)
            candidate = date.atTime(preferences.localTime)
        }
        // Resolve against the target zone so callers can safely convert across DST boundaries.
        return candidate.atZone(zone).toLocalDateTime()
    }
    private fun isQuiet(time: LocalTime, start: LocalTime?, end: LocalTime?): Boolean {
        if (start == null || end == null || start == end) return false
        return if (start < end) time >= start && time < end else time >= start || time < end
    }
}
