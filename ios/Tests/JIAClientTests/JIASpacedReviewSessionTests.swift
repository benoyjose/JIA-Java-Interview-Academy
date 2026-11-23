import XCTest
@testable import JIAClient
final class JIASpacedReviewSessionTests:XCTestCase{
 @MainActor func testRevealRequiredAndOneAdvancePerCard()throws{
  let session=JIASpacedReviewSession(cards:[JIAReviewCard(lessonSlug:"streams",title:"Streams",prompt:"Q",answer:"A")])
  XCTAssertThrowsError(try session.grade(4));session.reveal();let result=try session.grade(5);XCTAssertEqual(result.completed,1);XCTAssertTrue(result.finished);XCTAssertThrowsError(try session.grade(2))
 }
 @MainActor func testRatingBounds()throws{
  let session=JIASpacedReviewSession(cards:[JIAReviewCard(lessonSlug:"a",title:"A",prompt:"?",answer:"!")]);session.reveal();XCTAssertThrowsError(try session.grade(8))
 }
}
