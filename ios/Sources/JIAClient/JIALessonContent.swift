import Foundation

/// Versioned, forward-compatible representation of the shared lesson JSON payload.
public struct JIALessonContent: Decodable, Sendable, Equatable {
    public let schemaVersion: Int
    public let blocks: [JIALessonBlock]

    private enum CodingKeys: String, CodingKey { case schemaVersion, blocks }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let version = try container.decode(Int.self, forKey: .schemaVersion)
        guard version == 1 else {
            throw DecodingError.dataCorruptedError(forKey: .schemaVersion, in: container,
                debugDescription: "Unsupported lesson schema version: \(version)")
        }
        schemaVersion = version
        blocks = try container.decode([JIALessonBlock].self, forKey: .blocks)
    }
}

public struct JIALessonBlock: Decodable, Sendable, Equatable {
    public let type: String
    public let title: String?
    public let text: String?
    public let code: String?
    public let language: String?
    public let url: URL?
    /// Unknown block types are retained and ignored by the renderer, not treated as malformed JSON.
    public let isSupported: Bool

    private enum CodingKeys: String, CodingKey { case type, title, text, content, code, language, url }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        type = try container.decode(String.self, forKey: .type)
        title = try container.decodeIfPresent(String.self, forKey: .title)
        text = try container.decodeIfPresent(String.self, forKey: .text)
            ?? container.decodeIfPresent(String.self, forKey: .content)
        code = try container.decodeIfPresent(String.self, forKey: .code)
        language = try container.decodeIfPresent(String.self, forKey: .language)
        url = try container.decodeIfPresent(URL.self, forKey: .url)
        isSupported = Self.supportedTypes.contains(type)
    }

    private static let supportedTypes: Set<String> = [
        "overview", "objectives", "prerequisites", "interview_answer", "explanation",
        "code", "diagram", "media", "resource", "tradeoffs", "common_mistakes",
        "troubleshooting", "follow_up", "quiz"
    ]
}
