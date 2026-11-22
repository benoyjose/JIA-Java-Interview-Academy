import Foundation

public struct JIAAPIClient: Sendable {
    public let baseURL: URL
    private let session: URLSession

    public init(baseURL: URL, session: URLSession = .shared) {
        self.baseURL = baseURL
        self.session = session
    }

    public func request<T: Decodable & Sendable>(
        _ path: String,
        token: String? = nil,
        method: String = "GET",
        body: Data? = nil,
        as type: T.Type = T.self
    ) async throws -> T {
        let data = try await perform(path, token: token, method: method, body: body)
        return try JSONDecoder().decode(T.self, from: data)
    }

    /// Performs a request whose success response may be empty (for example HTTP 204 DELETE).
    @discardableResult
    public func perform(
        _ path: String,
        token: String? = nil,
        method: String = "GET",
        body: Data? = nil
    ) async throws -> Data {
        guard let url = URL(string: path, relativeTo: baseURL) else {
            throw JIAAPIError.invalidURL
        }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if body != nil { request.setValue("application/json", forHTTPHeaderField: "Content-Type") }
        if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }

        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw JIAAPIError.invalidResponse }
        guard (200..<300).contains(http.statusCode) else {
            throw JIAAPIError.httpStatus(http.statusCode)
        }
        return data
    }
}

public enum JIAAPIError: Error, Equatable {
    case invalidURL
    case invalidResponse
    case httpStatus(Int)
}

public struct JIATechnology: Codable, Sendable, Identifiable, Equatable {
    public let id: UUID
    public let slug: String
    public let name: String
    public let description: String?
    public let sortOrder: Int
}

public struct JIALessonSummary: Codable, Sendable, Identifiable, Equatable {
    public let id: UUID
    public let slug: String
    public let title: String
    public let summary: String?
    public let difficulty: String
    public let contentRevision: Int
}

public enum JIAJSONValue: Codable, Sendable, Equatable {
    case string(String)
    case number(Double)
    case bool(Bool)
    case object([String: JIAJSONValue])
    case array([JIAJSONValue])
    case null

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() { self = .null }
        else if let value = try? container.decode(Bool.self) { self = .bool(value) }
        else if let value = try? container.decode(Double.self) { self = .number(value) }
        else if let value = try? container.decode(String.self) { self = .string(value) }
        else if let value = try? container.decode([String: JIAJSONValue].self) { self = .object(value) }
        else if let value = try? container.decode([JIAJSONValue].self) { self = .array(value) }
        else { throw DecodingError.dataCorruptedError(in: container, debugDescription: "Unsupported JSON value") }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .string(let value): try container.encode(value)
        case .number(let value): try container.encode(value)
        case .bool(let value): try container.encode(value)
        case .object(let value): try container.encode(value)
        case .array(let value): try container.encode(value)
        case .null: try container.encodeNil()
        }
    }
}

public struct JIALessonProgress: Codable, Sendable, Equatable {
    public let lessonSlug: String
    public let contentRevision: Int
    public let status: String
    public let progressPercent: Double
    public let lastPosition: [String: JIAJSONValue]?
    public let updatedAt: String
}

public struct JIABookmark: Codable, Sendable, Equatable, Identifiable {
    public let lessonSlug: String
    public let title: String
    public let createdAt: String
    public var id: String { lessonSlug }
}
