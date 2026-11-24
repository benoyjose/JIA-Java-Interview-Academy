import XCTest
@testable import JIAClient
final class JIAMasteryInsightsTests:XCTestCase {
 func testUnassessedTopicIsNotMastered(){let t=JIAMasteryTopic(topicSlug:"jvm",topicName:"JVM",answers:0,correct:0,completedLessons:2);XCTAssertEqual(t.band,"Not assessed");XCTAssertEqual(t.accuracy,0)}
 func testAccuracyAndBands(){let t=JIAMasteryTopic(topicSlug:"java",topicName:"Java",answers:20,correct:18,completedLessons:4);XCTAssertEqual(t.accuracy,90);XCTAssertEqual(t.band,"Mastered")}
}