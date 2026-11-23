import Foundation

public struct JIAQueuedProgress: Codable, Sendable, Equatable, Identifiable {
    public let lessonSlug: String
    public let contentRevision: Int
    public let progressPercent: Double
    public let lastPosition: [String: JIAJSONValue]
    public let updatedAt: Int64
    public var id: String { lessonSlug }
}

public struct JIAQueuedBookmark: Codable, Sendable, Equatable, Identifiable {
    public let lessonSlug: String
    public let bookmarked: Bool
    public let updatedAt: Int64
    public var id: String { lessonSlug }
}

/// File-backed, atomic queue. Entries are coalesced by lesson and acknowledged only if
/// the server accepted the exact snapshot that was sent.
public final class JIAOfflineActivityQueue: @unchecked Sendable {
    private let fileURL: URL
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    public let maximumPendingItems: Int

    public init(fileURL: URL? = nil, maximumPendingItems: Int = 2_000) {
        self.maximumPendingItems = min(max(maximumPendingItems, 100), 100_000)
        self.fileURL = fileURL ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("JIAOfflineActivityQueue.json")
    }

    /// Pending coalesced records across progress and bookmark queues.
    public var pendingCount: Int { let state = load(); return state.progress.count + state.bookmarks.count }
    public var isAtCapacity: Bool { pendingCount >= maximumPendingItems }

    @discardableResult
    public func enqueueProgress(slug: String, revision: Int, percent: Double,
                               lastPosition: [String: JIAJSONValue]) -> Bool {
        var state = load()
        let current = state.progress[slug]
        guard current != nil || state.progress.count + state.bookmarks.count < maximumPendingItems else { return false }
        let isNewerRevision = current.map { revision >= $0.contentRevision } ?? true
        state.progress[slug] = JIAQueuedProgress(
            lessonSlug: slug,
            contentRevision: max(revision, current?.contentRevision ?? revision),
            progressPercent: max(percent, current?.progressPercent ?? percent),
            lastPosition: isNewerRevision ? lastPosition : (current?.lastPosition ?? lastPosition),
            updatedAt: max(now(), (current?.updatedAt ?? 0) + 1)
        )
        return save(state)
    }

    @discardableResult
    public func enqueueBookmark(slug: String, bookmarked: Bool) -> Bool {
        var state = load()
        let current = state.bookmarks[slug]
        guard current != nil || state.progress.count + state.bookmarks.count < maximumPendingItems else { return false }
        state.bookmarks[slug] = JIAQueuedBookmark(lessonSlug: slug, bookmarked: bookmarked,
            updatedAt: max(now(), (current?.updatedAt ?? 0) + 1))
        return save(state)
    }

    public func pendingProgress() -> [JIAQueuedProgress] {
        load().progress.values.sorted { $0.lessonSlug < $1.lessonSlug }
    }

    public func pendingBookmarks() -> [JIAQueuedBookmark] {
        load().bookmarks.values.sorted { $0.lessonSlug < $1.lessonSlug }
    }

    public func acknowledge(_ snapshot: JIAQueuedProgress) {
        var state = load()
        if state.progress[snapshot.lessonSlug]?.updatedAt == snapshot.updatedAt {
            state.progress.removeValue(forKey: snapshot.lessonSlug)
            save(state)
        }
    }

    public func acknowledge(_ snapshot: JIAQueuedBookmark) {
        var state = load()
        if state.bookmarks[snapshot.lessonSlug]?.updatedAt == snapshot.updatedAt {
            state.bookmarks.removeValue(forKey: snapshot.lessonSlug)
            save(state)
        }
    }

    private func now() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

    private func load() -> QueueState {
        guard let data = try? Data(contentsOf: fileURL),
              let state = try? decoder.decode(QueueState.self, from: data) else { return QueueState() }
        return state
    }

    private func save(_ state: QueueState) -> Bool {
        do {
            try FileManager.default.createDirectory(at: fileURL.deletingLastPathComponent(),
                                                   withIntermediateDirectories: true)
            let data = try encoder.encode(state)
            try data.write(to: fileURL, options: .atomic)
            return true
        } catch { return false }
    }

    private struct QueueState: Codable {
        var progress: [String: JIAQueuedProgress] = [:]
        var bookmarks: [String: JIAQueuedBookmark] = [:]
    }
}

@MainActor
public final class JIAOfflineActivitySyncService {
    private let client: JIAAPIClient
    private let queue: JIAOfflineActivityQueue

    public init(client: JIAAPIClient, queue: JIAOfflineActivityQueue) {
        self.client = client
        self.queue = queue
    }

    public func syncPending(token: String) async -> (progress: Int, bookmarks: Int, failures: Int) {
        var progressCount = 0
        var bookmarkCount = 0
        var failures = 0
        for item in queue.pendingProgress() {
            do {
                let body = try JSONEncoder().encode(ProgressPayload(contentRevision: item.contentRevision,
                    progressPercent: item.progressPercent, lastPosition: item.lastPosition))
                _ = try await client.perform("/api/v1/me/lesson-progress/\(item.lessonSlug)",
                    token: token, method: "PUT", body: body)
                queue.acknowledge(item)
                progressCount += 1
            } catch { failures += 1 }
        }
        for item in queue.pendingBookmarks() {
            do {
                _ = try await client.perform("/api/v1/me/bookmarks/\(item.lessonSlug)",
                    token: token, method: item.bookmarked ? "PUT" : "DELETE")
                queue.acknowledge(item)
                bookmarkCount += 1
            } catch { failures += 1 }
        }
        return (progressCount, bookmarkCount, failures)
    }

    private struct ProgressPayload: Encodable {
        let contentRevision: Int
        let progressPercent: Double
        let lastPosition: [String: JIAJSONValue]
    }
}
