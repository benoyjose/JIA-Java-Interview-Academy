import XCTest
@testable import JIAClient
final class JIAPracticeAttemptJournalTests:XCTestCase{
 func testAppendIsIdempotentAndAckRemovesEvent()async throws{let url=FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString).appendingPathComponent("attempts.json");let journal=JIAPracticeAttemptJournal(fileURL:url);let e=JIAPracticeAttempt(id:"e",sessionId:"s",promptId:"p",selectedOption:1,correct:true,elapsedMilliseconds:250);XCTAssertTrue(try await journal.append(e));XCTAssertFalse(try await journal.append(e));try await journal.acknowledge(eventIds:["e"]);XCTAssertTrue(await journal.pending().isEmpty)}
}
