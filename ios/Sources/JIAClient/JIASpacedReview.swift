import Foundation
import SwiftUI

public struct JIAReviewCard: Codable, Sendable, Identifiable, Equatable {
    public let id: String
    public let prompt: String
    public let answer: String
    public let dueAt: Date
}
public struct JIAReviewGrade: Codable, Sendable, Equatable {
    public let cardId: String
    public let grade: Int
    public let reviewedAt: Date
    public let dueAt: Date
}
public struct JIAReviewSession: Sendable, Equatable {
    public let cards: [JIAReviewCard]
    public private(set) var index = 0
    public private(set) var answerVisible = false
    public private(set) var grades: [JIAReviewGrade] = []
    public var current: JIAReviewCard? { cards.indices.contains(index) ? cards[index] : nil }
    public var isComplete: Bool { index >= cards.count }
    public init(cards: [JIAReviewCard]) { self.cards = cards }
    public mutating func reveal() { if !isComplete { answerVisible = true } }
    public mutating func grade(_ value: Int, at date: Date = .now) throws {
        guard (0...5).contains(value) else { throw ReviewError.invalidGrade }
        guard answerVisible, let card = current else { throw ReviewError.answerNotRevealed }
        let repetitions = grades.filter { $0.cardId == card.id }.count
        let days = value < 3 ? 1 : (repetitions == 0 ? 1 : repetitions == 1 ? 6 : 14)
        grades.append(JIAReviewGrade(cardId: card.id, grade: value, reviewedAt: date,
                                    dueAt: Calendar.current.date(byAdding: .day, value: days, to: date)!))
        index += 1
        answerVisible = false
    }
    public enum ReviewError: Error { case invalidGrade, answerNotRevealed }
}

public struct JIASpacedReviewView: View {
    @State private var session: JIAReviewSession
    public init(cards: [JIAReviewCard]) { _session = State(initialValue: JIAReviewSession(cards: cards)) }
    public var body: some View {
        List {
            if session.isComplete {
                ContentUnavailableView("Review complete", systemImage: "checkmark.circle",
                                      description: Text("\(session.grades.count) cards reviewed"))
            } else if let card = session.current {
                Section("Card \(session.index + 1) of \(session.cards.count)") {
                    Text(card.prompt).font(.title3)
                    if session.answerVisible { Text(card.answer) }
                    else { Button("Reveal answer") { session.reveal() } }
                    if session.answerVisible {
                        ForEach(0...5, id: \.self) { grade in
                            Button("Rate recall \(grade)") { try? session.grade(grade) }
                        }
                    }
                }
            }
        }.navigationTitle("Spaced review")
    }
}
