import Foundation
import CryptoKit

public struct JIAContentRelease: Codable, Sendable, Equatable {
    public let version: Int
    public let createdAt: String
    public let items: [JIAContentReleaseItem]
}

public struct JIAContentReleaseItem: Codable, Sendable, Equatable, Identifiable {
    public let id: UUID
    public let slug: String
    public let title: String
    public let contentRevision: Int
    public let checksum: String
}

public struct JIAReleasedLesson: Codable, Sendable, Equatable {
    public let slug: String
    public let contentRevision: Int
    public let contentJson: String
}

public struct JIAOfflineCachedLesson: Codable, Sendable, Equatable {
    public let slug: String
    public let contentRevision: Int
    public let contentJson: String
    public let checksum: String
}

public enum JIAOfflineContentError: Error, Equatable {
    case invalidReleaseVersion
    case invalidSlug(String)
    case missingLesson(String)
    case revisionMismatch(String)
    case checksumMismatch(String)
    case corruptManifest
}

/// A release is activated only after every lesson payload has passed its revision and checksum checks.
public final class JIAOfflineContentStore: @unchecked Sendable {
    private let root: URL
    private let fileManager: FileManager
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    public init(root: URL? = nil, fileManager: FileManager = .default) {
        self.root = root ?? fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("JIAOfflineContent", isDirectory: true)
        self.fileManager = fileManager
    }

    public var activeReleaseVersion: Int? {
        guard let data = try? Data(contentsOf: root.appendingPathComponent("active-release.json")),
              let pointer = try? decoder.decode(ActiveRelease.self, from: data) else { return nil }
        return pointer.version
    }

    public func activate(_ release: JIAContentRelease, lessons: [String: JIAReleasedLesson]) throws {
        guard release.version > 0 else { throw JIAOfflineContentError.invalidReleaseVersion }
        try fileManager.createDirectory(at: root, withIntermediateDirectories: true)
        let staging = root.appendingPathComponent("staging-\(release.version)", isDirectory: true)
        let destination = root.appendingPathComponent("release-\(release.version)", isDirectory: true)
        try? fileManager.removeItem(at: staging)
        try fileManager.createDirectory(at: staging, withIntermediateDirectories: true)

        do {
            for item in release.items {
                guard isSafeSlug(item.slug) else { throw JIAOfflineContentError.invalidSlug(item.slug) }
                guard let lesson = lessons[item.slug] else { throw JIAOfflineContentError.missingLesson(item.slug) }
                guard lesson.slug == item.slug, lesson.contentRevision == item.contentRevision else {
                    throw JIAOfflineContentError.revisionMismatch(item.slug)
                }
                let checksum = Self.sha256(lesson.contentJson)
                guard checksum.caseInsensitiveCompare(item.checksum) == .orderedSame else {
                    throw JIAOfflineContentError.checksumMismatch(item.slug)
                }
                let cached = JIAOfflineCachedLesson(slug: lesson.slug, contentRevision: lesson.contentRevision,
                    contentJson: lesson.contentJson, checksum: checksum)
                let data = try encoder.encode(cached)
                try data.write(to: staging.appendingPathComponent(item.slug + ".json"), options: .atomic)
            }

            try encoder.encode(release).write(to: staging.appendingPathComponent("release.json"), options: .atomic)
            if fileManager.fileExists(atPath: destination.path) {
                try fileManager.removeItem(at: destination)
            }
            try fileManager.moveItem(at: staging, to: destination)
            let pointer = try encoder.encode(ActiveRelease(version: release.version))
            try pointer.write(to: root.appendingPathComponent("active-release.json"), options: .atomic)
        } catch {
            try? fileManager.removeItem(at: staging)
            throw error
        }
    }

    public func cachedLesson(slug: String) -> JIAOfflineCachedLesson? {
        guard isSafeSlug(slug), let version = activeReleaseVersion else { return nil }
        let url = root.appendingPathComponent("release-\(version)", isDirectory: true)
            .appendingPathComponent(slug + ".json")
        guard let data = try? Data(contentsOf: url),
              let lesson = try? decoder.decode(JIAOfflineCachedLesson.self, from: data),
              Self.sha256(lesson.contentJson).caseInsensitiveCompare(lesson.checksum) == .orderedSame else {
            return nil
        }
        return lesson
    }

    public static func sha256(_ content: String) -> String {
        SHA256.hash(data: Data(content.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    private func isSafeSlug(_ slug: String) -> Bool {
        !slug.isEmpty && slug.range(of: "^[A-Za-z0-9_-]+$", options: .regularExpression) != nil
    }

    private struct ActiveRelease: Codable { let version: Int }
}

@MainActor
public final class JIAOfflineSyncService {
    public private(set) var progress: Double = 0
    public private(set) var statusMessage: String?
    private let client: JIAAPIClient
    private let store: JIAOfflineContentStore

    public init(client: JIAAPIClient, store: JIAOfflineContentStore) {
        self.client = client
        self.store = store
    }

    public func syncLatestRelease() async throws -> Bool {
        let release: JIAContentRelease = try await client.request("/api/v1/content/releases/latest")
        if let active = store.activeReleaseVersion, active >= release.version {
            statusMessage = "Offline content is already current (release \(active))."
            progress = 1
            return false
        }
        var lessons: [String: JIAReleasedLesson] = [:]
        let total = max(release.items.count, 1)
        for (index, item) in release.items.enumerated() {
            let lesson: JIAReleasedLesson = try await client.request(
                "/api/v1/content/releases/\(release.version)/lessons/\(item.slug)")
            lessons[item.slug] = lesson
            progress = Double(index + 1) / Double(total)
        }
        try store.activate(release, lessons: lessons)
        statusMessage = "Offline release \(release.version) is ready."
        return true
    }
}
