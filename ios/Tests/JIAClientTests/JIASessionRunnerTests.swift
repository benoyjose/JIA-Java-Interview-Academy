import XCTest
@testable import JIAClient
@MainActor
final class JIASessionRunnerTests:XCTestCase{
 func testAnswerSubmitAndFinish(){let p=JIAPrompt(id:"1",question:"Q",options:["A","B"],correctOption:1,explanation:"Because");let runner=JIASessionRunner(prompts:[p]);runner.answer(1);runner.submit();XCTAssertEqual(runner.score,1);runner.next();XCTAssertTrue(runner.completed)}
 func testInvalidAnswerAndEmptySessionAreSafe(){let runner=JIASessionRunner(prompts:[]);runner.answer(9);runner.submit();XCTAssertFalse(runner.submitted);XCTAssertNil(runner.current)}
}
