import XCTest
@testable import JIAClient
final class JIASpacedRepetitionPolicyTests: XCTestCase {
 func testAgainResetsAndRatingsIncreaseInterval(){let p=JIASpacedRepetitionPolicy();XCTAssertEqual(p.nextIntervalDays(current:12,rating:.again),1);XCTAssertEqual(p.nextIntervalDays(current:5,rating:.good),10);XCTAssertEqual(p.nextIntervalDays(current:5,rating:.easy),14);}
 func testNegativeIntervalsAreSafelyClamped(){XCTAssertEqual(JIASpacedRepetitionPolicy().nextIntervalDays(current:-4,rating:.hard),1)}
}