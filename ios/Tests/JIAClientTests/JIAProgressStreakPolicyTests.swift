import XCTest
@testable import JIAClient
final class JIAProgressStreakPolicyTests: XCTestCase {
 func testStreakIncludesTodayOrYesterdayAndStopsAtGap() {
  var calendar=Calendar(identifier:.gregorian); calendar.timeZone=TimeZone(secondsFromGMT:0)!
  let today=calendar.startOfDay(for:Date(timeIntervalSince1970:1_800_000_000))
  let yesterday=calendar.date(byAdding:.day,value:-1,to:today)!
  let twoDaysAgo=calendar.date(byAdding:.day,value:-2,to:today)!
  XCTAssertEqual(JIAProgressStreakPolicy.currentStreak(activityDays:[today,yesterday,twoDaysAgo],today:today,calendar:calendar),3)
  XCTAssertEqual(JIAProgressStreakPolicy.currentStreak(activityDays:[],today:today,calendar:calendar),0)
 }
}
