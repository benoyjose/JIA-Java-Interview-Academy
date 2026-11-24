import Foundation
import SwiftUI

public struct JIAPracticeEvidence: Sendable, Equatable {
    public let id: String
    public let lastReviewedAt: Date?
    public let correctStreak: Int
    public let recentWrong: Int
    public let confidencePercent: Int

    public init(id: String, lastReviewedAt: Date?, correctStreak: Int, recentWrong: Int, confidencePercent: Int) {
        self.id = id
        self.lastReviewedAt = lastReviewedAt
        self.correctStreak = correctStreak
        self.recentWrong = recentWrong
        self.confidencePercent = confidencePercent
    }
}

public struct JIAPracticeQueueItem: Identifiable, Sendable, Equatable {
    public let id: String
    public let dueAt: Date
    public let intervalDays: Int
    public let priority: Int
    public let rationale: String
}

public enum JIAPracticeQueuePolicy {
    public static func intervalDays(streak: Int, wrong: Int, confidence: Int) -> Int {
        let score = min(100, max(0, confidence))
        if wrong > 0 || score < 50 || streak <= 0 { return 1 }
        if score < 75 || streak == 1 { return 2 }
        if streak == 2 { return 4 }
        return min(30, 4 * (1 + min(6, streak - 2)))
    }

    public static func due(_ evidence: [JIAPracticeEvidence], now: Date, limit: Int = 30) -> [JIAPracticeQueueItem] {
        precondition((1...100).contains(limit), "limit must be 1...100")
        let candidates: [JIAPracticeQueueItem] = evidence.compactMap { item in
            guard !item.id.isEmpty, let last = item.lastReviewedAt else { return nil }
            let interval = intervalDays(streak: item.correctStreak, wrong: item.recentWrong, confidence: item.confidencePercent)
            let dueAt = last.addingTimeInterval(TimeInterval(interval) * 86_400)
            guard dueAt <= now else { return nil }
            let confidence = min(100, max(0, item.confidencePercent))
            let priority = min(100, 50 + max(0, item.recentWrong) * 10 + (100 - confidence) / 2)
            return JIAPracticeQueueItem(
                id: item.id,
                dueAt: dueAt,
                intervalDays: interval,
                priority: priority,
                rationale: item.recentWrong > 0 ? "Review a missed concept" : "Scheduled recall"
            )
        }
        return candidates.sorted { lhs, rhs in
            if lhs.priority != rhs.priority { return lhs.priority > rhs.priority }
            if lhs.dueAt != rhs.dueAt { return lhs.dueAt < rhs.dueAt }
            return lhs.id < rhs.id
        }.prefix(limit).map { $0 }
    }
}

public struct JIAPracticeQueueView: View {
    public let items: [JIAPracticeQueueItem]
    public init(items: [JIAPracticeQueueItem]) { self.items = items }

    public var body: some View {
        List {
            Section("Review queue · \(items.count) due") {
                if items.isEmpty {
                    ContentUnavailableView(
                        "You're caught up",
                        systemImage: "checkmark.circle",
                        description: Text("Reviews return when evidence is due.")
                    )
                }
                ForEach(items) { item in
                    VStack(alignment: .leading, spacing: 5) {
                        Text(item.id).font(.headline)
                        Text("Priority \(item.priority) · interval \(item.intervalDays)d")
                            .font(.caption).foregroundStyle(.secondary)
                        Text(item.rationale).font(.subheadline)
                    }.padding(.vertical, 4)
                }
            }
        }.navigationTitle("Practice queue")
    }
}
