import XCTest
@testable import JIAClient
final class JIASessionEffortInsightsTests:XCTestCase {
 func testAccuracyPaceAndReviewFlag(){
  XCTAssertEqual(JIASessionEffortInsights.calculate(correct:6,total:10,durationSeconds:300),JIASessionEffortInsight(accuracyPercent:60,averageSecondsPerQuestion:30,needsReview:true))
 }
 func testEmptySessionAvoidsDivisionByZero(){
  XCTAssertEqual(JIASessionEffortInsights.calculate(correct:0,total:0,durationSeconds:0),JIASessionEffortInsight(accuracyPercent:0,averageSecondsPerQuestion:nil,needsReview:false))
 }
}