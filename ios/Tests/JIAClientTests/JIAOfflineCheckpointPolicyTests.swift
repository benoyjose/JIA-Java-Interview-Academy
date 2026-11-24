import XCTest
@testable import JIAClient

final class JIAOfflineCheckpointPolicyTests: XCTestCase {
    func testFirstValidCheckpointPersists() {
        let checkpoint = OfflineCheckpoint(lessonID: "java-threads", position: 12, sequence: 1, recordedAt: .now)
        XCTAssertTrue(OfflineCheckpointPolicy.shouldPersist(previous: nil, proposed: checkpoint, minimumInterval: 2))
    }

    func testRejectsOutOfOrderOrTooFrequentCheckpoint() {
        let date = Date(timeIntervalSince1970: 100)
        let old = OfflineCheckpoint(lessonID: "java-threads", position: 20, sequence: 4, recordedAt: date)
        let rollback = OfflineCheckpoint(lessonID: "java-threads", position: 15, sequence: 5, recordedAt: date.addingTimeInterval(5))
        let fast = OfflineCheckpoint(lessonID: "java-threads", position: 25, sequence: 5, recordedAt: date.addingTimeInterval(1))
        XCTAssertFalse(OfflineCheckpointPolicy.shouldPersist(previous: old, proposed: rollback, minimumInterval: 2))
        XCTAssertFalse(OfflineCheckpointPolicy.shouldPersist(previous: old, proposed: fast, minimumInterval: 2))
    }

    func testAllowsAdvancingCheckpointAfterWindow() {
        let date = Date(timeIntervalSince1970: 100)
        let old = OfflineCheckpoint(lessonID: "java-threads", position: 20, sequence: 4, recordedAt: date)
        let next = OfflineCheckpoint(lessonID: "java-threads", position: 25, sequence: 5, recordedAt: date.addingTimeInterval(3))
        XCTAssertTrue(OfflineCheckpointPolicy.shouldPersist(previous: old, proposed: next, minimumInterval: 2))
    }
}
