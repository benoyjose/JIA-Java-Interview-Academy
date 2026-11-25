package com.jia.academy.learning.api;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LearnerReminderPreferencesControllerTest {
    private LearnerReminderPreferencesController.ReminderPreferencesRequest valid() {
        return new LearnerReminderPreferencesController.ReminderPreferencesRequest(
            true, "Asia/Kolkata", "20:30", "22:00", "07:00", 45, 5);
    }

    @Test void acceptsValidTimezoneGoalAndOvernightQuietWindow() {
        assertDoesNotThrow(() -> LearnerReminderPreferencesController.validate(valid()));
    }

    @Test void rejectsInvalidTimezoneAndClockValue() {
        var invalidZone = valid();
        assertThrows(ResponseStatusException.class, () ->
            LearnerReminderPreferencesController.validate(new LearnerReminderPreferencesController.ReminderPreferencesRequest(
                true, "Not/AZone", "20:30", "", "", 30, 5)));
        assertThrows(ResponseStatusException.class, () ->
            LearnerReminderPreferencesController.validate(new LearnerReminderPreferencesController.ReminderPreferencesRequest(
                true, "UTC", "25:90", "", "", 30, 5)));
    }

    @Test void requiresQuietHoursAsPair() {
        var request = new LearnerReminderPreferencesController.ReminderPreferencesRequest(
            true, "UTC", "19:00", "22:00", "", 30, 5);
        var error = assertThrows(ResponseStatusException.class, () ->
            LearnerReminderPreferencesController.validate(request));
        assertEquals(400, error.getStatusCode().value());
    }

    @Test void boundsStudyGoals() {
        assertThrows(ResponseStatusException.class, () ->
            LearnerReminderPreferencesController.validate(new LearnerReminderPreferencesController.ReminderPreferencesRequest(
                false, "UTC", "19:00", "", "", 241, 5)));
        assertThrows(ResponseStatusException.class, () ->
            LearnerReminderPreferencesController.validate(new LearnerReminderPreferencesController.ReminderPreferencesRequest(
                false, "UTC", "19:00", "", "", 30, 0)));
    }
}
