import Foundation

public enum JIAProgressStreakPolicy {
    /// Counts consecutive calendar days with at least one completed learning activity.
    public static func currentStreak(activityDays: Set<Date>, today: Date = Date(),
                                     calendar input: Calendar = .current) -> Int {
        var calendar = input
        calendar.timeZone = input.timeZone
        let normalized = Set(activityDays.map { calendar.startOfDay(for: $0) })
        var day = calendar.startOfDay(for: today)
        if !normalized.contains(day) {
            guard let yesterday = calendar.date(byAdding: .day, value: -1, to: day),
                  normalized.contains(yesterday) else { return 0 }
            day = yesterday
        }
        var count = 0
        while normalized.contains(day) {
            count += 1
            guard let previous = calendar.date(byAdding: .day, value: -1, to: day) else { break }
            day = previous
        }
        return count
    }
}
