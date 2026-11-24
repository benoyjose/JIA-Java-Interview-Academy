import XCTest
@testable import JIAClient

final class JIASpacedReviewTests: XCTestCase {
    func testAnswerGateAndAdvance() throws {
        let date = Date(timeIntervalSince1970: 1_800_000_000)
        let card = JIAReviewCard(id: "one", prompt: "Question", answer: "Answer", dueAt: date)
        var session = JIAReviewSession(cards: [card])
        XCTAssertThrowsError(try session.grade(4, at: date))
        session.reveal()
        try session.grade(4, at: date)
        XCTAssertTrue(session.isComplete)
        XCTAssertEqual(session.grades.count, 1)
        XCTAssertEqual(session.grades[0].dueAt.timeIntervalSince(date), 86400, accuracy: 1)
    }
    func testInvalidGradeDoesNotAdvance() {
        var session = JIAReviewSession(cards: [JIAReviewCard(id: "a", prompt: "P", answer: "A", dueAt: .now)])
        session.reveal()
        XCTAssertThrowsError(try session.grade(7))
        XCTAssertEqual(session.index, 0)
    }
}
