import XCTest
@testable import JIAClient
final class JIAPracticeQueueTests:XCTestCase{
 func testWeakDueEvidenceRanksFirst(){let now=Date(timeIntervalSince1970:1795478400);let q=JIAPracticeQueuePolicy.due([JIAPracticeEvidence(id:"stable",lastReviewedAt:now.addingTimeInterval(-864000),correctStreak:5,recentWrong:0,confidencePercent:95),JIAPracticeEvidence(id:"weak",lastReviewedAt:now.addingTimeInterval(-259200),correctStreak:1,recentWrong:2,confidencePercent:30)],now:now);XCTAssertEqual(q.first?.id,"weak");XCTAssertEqual(q.first?.intervalDays,1)}
 func testFutureAndMissingEvidenceAreExcluded(){let now=Date();XCTAssertTrue(JIAPracticeQueuePolicy.due([JIAPracticeEvidence(id:"future",lastReviewedAt:now,correctStreak:5,recentWrong:0,confidencePercent:99),JIAPracticeEvidence(id:"missing",lastReviewedAt:nil,correctStreak:0,recentWrong:0,confidencePercent:0)],now:now).isEmpty)}
}
