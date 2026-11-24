import XCTest
@testable import JIAClient
@MainActor
final class JIASessionRunnerTests:XCTestCase{
 private let prompt=JIAPrompt(id:"1",question:"Q",options:["A","B"],correctOption:1,explanation:"Because")
 func testAnswerSubmitAndFinish(){let runner=JIASessionRunner(prompts:[prompt]);runner.answer(1);runner.submit();XCTAssertEqual(runner.score,1);runner.next();XCTAssertTrue(runner.completed)}
 func testInvalidAnswerAndEmptySessionAreSafe(){let runner=JIASessionRunner(prompts:[]);runner.answer(9);runner.submit();XCTAssertFalse(runner.submitted);XCTAssertNil(runner.current)}
 func testResultMetricsIncludeUnansweredInMistakeReview(){
  let second=JIAPrompt(id:"2",question:"Q2",options:["X","Y"],correctOption:0,explanation:"Correct")
  let runner=JIASessionRunner(prompts:[prompt,second]);runner.answer(0);runner.submit();runner.next();runner.submit();runner.next()
  XCTAssertEqual(runner.score,0);XCTAssertEqual(runner.accuracyPercent,0);XCTAssertEqual(runner.answeredCount,1);XCTAssertEqual(runner.missedPrompts.map(\.id),["1","2"])
 }
 func testMistakeReviewRequiresCompletionAndCanReturnToSummary(){
  let runner=JIASessionRunner(prompts:[prompt]);runner.showMistakeReview();XCTAssertFalse(runner.reviewingMistakes)
  runner.answer(0);runner.submit();runner.next();runner.showMistakeReview();XCTAssertTrue(runner.reviewingMistakes)
  runner.returnToSummary();XCTAssertFalse(runner.reviewingMistakes)
 }
}
