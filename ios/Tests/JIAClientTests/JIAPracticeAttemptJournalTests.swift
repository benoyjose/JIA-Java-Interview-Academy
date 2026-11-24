import XCTest
@testable import JIAClient
final class JIAPracticeAttemptJournalTests: XCTestCase {
 func testAppendIsIdempotentAndAckRemovesEvent() async throws {
  let url=FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString).appendingPathComponent("attempts.json")
  let journal=JIAPracticeAttemptJournal(fileURL:url)
  let event=JIAPracticeAttempt(id:"e",sessionId:"s",promptId:"p",selectedOption:1,correct:true,elapsedMilliseconds:250)
  let first=try await journal.append(event)
  let duplicate=try await journal.append(event)
  XCTAssertTrue(first);XCTAssertFalse(duplicate)
  try await journal.acknowledge(eventIds:["e"])
  let pending=await journal.pending()
  XCTAssertTrue(pending.isEmpty)
 }
}
