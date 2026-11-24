import Foundation

public struct JIAStudyReminderPreferences: Sendable, Equatable {
    public let enabled: Bool
    public let hour: Int
    public let minute: Int
    public let quietStartMinute: Int?
    public let quietEndMinute: Int?
    public init(enabled: Bool, hour: Int, minute: Int, quietStartMinute: Int? = nil, quietEndMinute: Int? = nil) {
        self.enabled = enabled; self.hour = hour; self.minute = minute
        self.quietStartMinute = quietStartMinute; self.quietEndMinute = quietEndMinute
    }
}
public enum JIAStudyReminderPolicy {
    public static func nextDate(preferences: JIAStudyReminderPreferences, now: Date, calendar input: Calendar) -> Date? {
        guard preferences.enabled, (0...23).contains(preferences.hour), (0...59).contains(preferences.minute) else { return nil }
        var calendar = input
        let current = calendar.dateComponents([.year, .month, .day], from: now)
        guard let today = calendar.date(from: current) else { return nil }
        var components = DateComponents(); components.hour = preferences.hour; components.minute = preferences.minute
        var candidate = calendar.nextDate(after: today.addingTimeInterval(-1), matching: components, matchingPolicy: .nextTime, repeatedTimePolicy: .first, direction: .forward)
        if let first = candidate, first <= now {
            candidate = calendar.nextDate(after: today.addingTimeInterval(24 * 60 * 60 - 1), matching: components, matchingPolicy: .nextTime, repeatedTimePolicy: .first, direction: .forward)
        }
        guard let date = candidate else { return nil }
        let local = calendar.dateComponents([.hour, .minute], from: date)
        let minute = (local.hour ?? 0) * 60 + (local.minute ?? 0)
        if isQuiet(minute, start: preferences.quietStartMinute, end: preferences.quietEndMinute) {
            return calendar.nextDate(after: date, matching: components, matchingPolicy: .nextTime, repeatedTimePolicy: .first, direction: .forward)
        }
        return date
    }
    private static func isQuiet(_ value: Int, start: Int?, end: Int?) -> Bool {
        guard let start, let end, (0..<1440).contains(start), (0..<1440).contains(end), start != end else { return false }
        return start < end ? value >= start && value < end : value >= start || value < end
    }
}
