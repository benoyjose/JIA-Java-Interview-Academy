import XCTest
@testable import JIAClient
final class JIAStudyPlanTests: XCTestCase {
    func testBudgetClampAndTaskTruncation() {
        XCTAssertEqual(JIAStudyPlanBudget.clamp(0), 10)
        XCTAssertEqual(JIAStudyPlanBudget.clamp(500), 180)
        let tasks = [
            JIAStudyPlanTask(kind: "TOPIC", title: "Concurrency", lessonSlug: nil, topicSlug: "threads", minutes: 15, reason: "Weak"),
            JIAStudyPlanTask(kind: "LESSON", title: "Streams", lessonSlug: "streams", topicSlug: nil, minutes: 20, reason: "Resume")
        ]
        let plan = JIAStudyPlanBudget.allocate(tasks, minutes: 20)
        XCTAssertEqual(plan.map(\.minutes), [15, 5])
        XCTAssertEqual(plan.reduce(0) { $0 + $1.minutes }, 20)
    }
    func testEmptyPlanIsStable() {
        XCTAssertTrue(JIAStudyPlanBudget.allocate([], minutes: 30).isEmpty)
    }
}
