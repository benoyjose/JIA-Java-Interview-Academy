import XCTest
@testable import JIAClient

final class JIAStudyReminderPolicyTests: XCTestCase {
    func testDisabledPolicyDoesNotSchedule() {
        var calendar = Calendar(identifier: .gregorian); calendar.timeZone = TimeZone(identifier: "Asia/Kolkata")!
        let preferences = JIAStudyReminderPreferences(enabled: false, hour: 8, minute: 0)
        XCTAssertNil(JIAStudyReminderPolicy.nextDate(preferences: preferences, now: Date(), calendar: calendar))
    }
    func testPastReminderMovesToTomorrowInConfiguredCalendar() {
        var calendar = Calendar(identifier: .gregorian); calendar.timeZone = TimeZone(identifier: "Asia/Kolkata")!
        let now = calendar.date(from: DateComponents(year: 2026, month: 11, day: 24, hour: 9))!
        let prefs = JIAStudyReminderPreferences(enabled: true, hour: 8, minute: 0)
        let next = JIAStudyReminderPolicy.nextDate(preferences: prefs, now: now, calendar: calendar)!
        XCTAssertEqual(calendar.component(.day, from: next), 25)
        XCTAssertEqual(calendar.component(.hour, from: next), 8)
    }
}
