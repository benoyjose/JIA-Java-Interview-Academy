package com.jia.academy.app.learning
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test
class StudyReminderWindowTest {
 @Test fun handlesOvernightQuietHours(){val r=StudyReminderPlanner.evaluate(LocalTime.of(23,0),LocalTime.of(22,0),LocalTime.of(7,0));assertFalse(r.shouldNotify);assertEquals(LocalTime.of(7,0),r.nextAllowed)}
 @Test fun permitsOutsideQuietHours(){assertTrue(StudyReminderPlanner.evaluate(LocalTime.of(12,0),LocalTime.of(22,0),LocalTime.of(7,0)).shouldNotify)}
 @Test fun handlesDaytimeQuietHours(){assertFalse(StudyReminderPlanner.evaluate(LocalTime.of(13,0),LocalTime.of(12,0),LocalTime.of(14,0)).shouldNotify)}
}