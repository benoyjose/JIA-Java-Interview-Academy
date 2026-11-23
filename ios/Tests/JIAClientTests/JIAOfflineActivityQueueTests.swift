import Foundation
import XCTest
@testable import JIAClient

final class JIAOfflineActivityQueueTests: XCTestCase {
    private var fileURL: URL!
    private var queue: JIAOfflineActivityQueue!

    override func setUp() {
        super.setUp()
        fileURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("jia-activity-\(UUID().uuidString).json")
        queue = JIAOfflineActivityQueue(fileURL: fileURL)
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: fileURL)
        super.tearDown()
    }

    func testQueuePersistsAndMergesProgressMonotonically() {
        queue.enqueueProgress(slug: "lesson-1", revision: 4, percent: 40,
            lastPosition: ["block": .number(3)])
        let first = queue.pendingProgress().first!
        queue.enqueueProgress(slug: "lesson-1", revision: 4, percent: 80,
            lastPosition: ["block": .number(8)])
        queue.enqueueProgress(slug: "lesson-1", revision: 2, percent: 15,
            lastPosition: ["block": .number(1)])

        let reopened = JIAOfflineActivityQueue(fileURL: fileURL)
        let merged = reopened.pendingProgress().first
        XCTAssertEqual(merged?.contentRevision, 4)
        XCTAssertEqual(merged?.progressPercent, 80)
        XCTAssertEqual(merged?.lastPosition["block"], .number(8))
        XCTAssertGreaterThan(merged?.updatedAt ?? 0, first.updatedAt)
    }

    func testAcknowledgingOldProgressSnapshotPreservesNewerMutation() {
        queue.enqueueProgress(slug: "lesson-2", revision: 1, percent: 25,
            lastPosition: ["anchor": .string("first")])
        let sent = queue.pendingProgress().first!
        queue.enqueueProgress(slug: "lesson-2", revision: 1, percent: 70,
            lastPosition: ["anchor": .string("newer")])

        queue.acknowledge(sent)

        XCTAssertEqual(queue.pendingProgress().first?.progressPercent, 70)
        XCTAssertEqual(queue.pendingProgress().first?.lastPosition["anchor"], .string("newer"))
    }

    func testBookmarkQueueKeepsLastIntentAndSnapshotAcknowledgement() {
        queue.enqueueBookmark(slug: "lesson-3", bookmarked: true)
        let sent = queue.pendingBookmarks().first!
        queue.enqueueBookmark(slug: "lesson-3", bookmarked: false)

        queue.acknowledge(sent)

        XCTAssertEqual(queue.pendingBookmarks().first?.bookmarked, false)
    }

    func testQueueRejectsNewDistinctRecordsAtCapacityButAllowsCoalescing() {
        queue = JIAOfflineActivityQueue(fileURL: fileURL, maximumPendingItems: 100)
        for index in 0..<100 {
            XCTAssertTrue(queue.enqueueBookmark(slug: "lesson-\(index)", bookmarked: true))
        }
        XCTAssertFalse(queue.enqueueProgress(slug: "new-lesson", revision: 1, percent: 5, lastPosition: [:]))
        XCTAssertTrue(queue.enqueueBookmark(slug: "lesson-0", bookmarked: false))
        XCTAssertEqual(queue.pendingCount, 100)
        XCTAssertTrue(queue.isAtCapacity)
    }

    func testCapacityIsClampedToSupportedSafeRange() {
        let tiny = JIAOfflineActivityQueue(fileURL: fileURL, maximumPendingItems: 1)
        XCTAssertEqual(tiny.maximumPendingItems, 100)
        let huge = JIAOfflineActivityQueue(fileURL: fileURL, maximumPendingItems: 1_000_000)
        XCTAssertEqual(huge.maximumPendingItems, 100_000)
    }

}
