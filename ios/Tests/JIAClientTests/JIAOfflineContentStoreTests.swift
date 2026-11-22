import Foundation
import XCTest
@testable import JIAClient

final class JIAOfflineContentStoreTests: XCTestCase {
    private var root: URL!
    private var store: JIAOfflineContentStore!

    override func setUp() {
        super.setUp()
        root = FileManager.default.temporaryDirectory
            .appendingPathComponent("jia-offline-tests-\(UUID().uuidString)", isDirectory: true)
        store = JIAOfflineContentStore(root: root)
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: root)
        super.tearDown()
    }

    func testVerifiedReleaseActivatesAndCanBeReadOffline() throws {
        let json = #"{"schemaVersion":1,"blocks":[{"type":"text","text":"offline"}]}"#
        let lesson = JIAReleasedLesson(slug: "java-memory", contentRevision: 3, contentJson: json)
        let release = manifest(version: 1, slug: lesson.slug, revision: 3, content: json)

        try store.activate(release, lessons: [lesson.slug: lesson])

        XCTAssertEqual(store.activeReleaseVersion, 1)
        XCTAssertEqual(store.cachedLesson(slug: lesson.slug)?.contentJson, json)
        XCTAssertEqual(store.cachedLesson(slug: lesson.slug)?.contentRevision, 3)
    }

    func testChecksumFailureDoesNotSwitchActiveRelease() throws {
        let v1Json = #"{"blocks":["stable"]}"#
        let v1 = JIAReleasedLesson(slug: "stable-lesson", contentRevision: 1, contentJson: v1Json)
        try store.activate(manifest(version: 1, slug: v1.slug, revision: 1, content: v1Json),
                           lessons: [v1.slug: v1])

        let v2Json = #"{"blocks":["tampered"]}"#
        let v2 = JIAReleasedLesson(slug: v1.slug, contentRevision: 2, contentJson: v2Json)
        let badManifest = manifest(version: 2, slug: v1.slug, revision: 2, content: "different payload")

        XCTAssertThrowsError(try store.activate(badManifest, lessons: [v1.slug: v2])) { error in
            XCTAssertEqual(error as? JIAOfflineContentError, .checksumMismatch(v1.slug))
        }
        XCTAssertEqual(store.activeReleaseVersion, 1)
        XCTAssertEqual(store.cachedLesson(slug: v1.slug)?.contentJson, v1Json)
    }

    func testMissingPayloadOrRevisionMismatchLeavesPreviousReleaseActive() throws {
        let content = #"{"blocks":["known-good"]}"#
        let item = JIAReleasedLesson(slug: "lesson-x", contentRevision: 1, contentJson: content)
        try store.activate(manifest(version: 1, slug: item.slug, revision: 1, content: content),
                           lessons: [item.slug: item])

        let next = manifest(version: 2, slug: item.slug, revision: 2, content: content)
        XCTAssertThrowsError(try store.activate(next, lessons: [:]))
        XCTAssertEqual(store.activeReleaseVersion, 1)
        XCTAssertEqual(store.cachedLesson(slug: item.slug)?.contentRevision, 1)
    }

    func testReleaseRejectsUnsafeSlugsAndInvalidRevision() throws {
        let content = #"{"ok":true}"#
        let unsafe = JIAContentReleaseItem(id: UUID(), slug: "../escape", title: "Unsafe",
            contentRevision: 1, checksum: JIAOfflineContentStore.sha256(content))
        let release = JIAContentRelease(version: 1, createdAt: "now", items: [unsafe])
        let lesson = JIAReleasedLesson(slug: unsafe.slug, contentRevision: 1, contentJson: content)

        XCTAssertThrowsError(try store.activate(release, lessons: [unsafe.slug: lesson])) { error in
            XCTAssertEqual(error as? JIAOfflineContentError, .invalidSlug(unsafe.slug))
        }
        XCTAssertNil(store.activeReleaseVersion)
    }

    private func manifest(version: Int, slug: String, revision: Int, content: String) -> JIAContentRelease {
        JIAContentRelease(version: version, createdAt: "2026-11-22T00:00:00Z", items: [
            JIAContentReleaseItem(id: UUID(), slug: slug, title: slug, contentRevision: revision,
                checksum: JIAOfflineContentStore.sha256(content))
        ])
    }
}
