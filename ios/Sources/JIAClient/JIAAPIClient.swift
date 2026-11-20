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
        return try JSONDecoder().decode(T.self, from: data)
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
