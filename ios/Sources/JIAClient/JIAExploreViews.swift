import SwiftUI

public struct JIACategory: Codable, Sendable, Identifiable, Equatable {
    public let id: UUID
    public let slug: String
    public let name: String
    public let technologySlug: String?
}

public struct JIACategoryListView: View {
    let technology: JIATechnology
    let client: JIAAPIClient
    @State private var categories: [JIACategory] = []
    @State private var loading = true
    @State private var errorMessage: String?

    public init(technology: JIATechnology, client: JIAAPIClient) {
        self.technology = technology
        self.client = client
    }

    public var body: some View {
        List {
            if loading { ProgressView("Loading categories") }
            if let errorMessage { Text(errorMessage).foregroundStyle(.red) }
            ForEach(categories) { category in
                NavigationLink(category.name) {
                    JIALessonListView(category: category, client: client)
                }
            }
        }
        .navigationTitle(technology.name)
        .task { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        loading = true
        defer { loading = false }
        do {
            categories = try await client.request(
                "/api/v1/technologies/\(technology.slug)/categories")
            errorMessage = nil
        } catch { errorMessage = error.localizedDescription }
    }
}

public struct JIALessonListView: View {
    let category: JIACategory
    let client: JIAAPIClient
    @State private var lessons: [JIALessonSummary] = []
    @State private var loading = true
    @State private var errorMessage: String?

    public init(category: JIACategory, client: JIAAPIClient) {
        self.category = category
        self.client = client
    }

    public var body: some View {
        List {
            if loading { ProgressView("Loading lessons") }
            if let errorMessage { Text(errorMessage).foregroundStyle(.red) }
            ForEach(lessons) { lesson in
                NavigationLink {
                    JIALessonDetailView(lesson: lesson, client: client)
                } label: {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(lesson.title).font(.headline)
                        Text(lesson.difficulty).font(.caption).foregroundStyle(.secondary)
                        if let summary = lesson.summary { Text(summary).font(.subheadline) }
                    }
                }
            }
        }
        .navigationTitle(category.name)
        .task { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        loading = true
        defer { loading = false }
        do {
            lessons = try await client.request(
                "/api/v1/categories/\(category.slug)/lessons")
            errorMessage = nil
        } catch { errorMessage = error.localizedDescription }
    }
}

private struct JIAFullLesson: Decodable, Sendable {
    let contentJson: String?
    let contentRevision: Int?
}

public struct JIALessonDetailView: View {
    let lesson: JIALessonSummary
    let client: JIAAPIClient
    @State private var content: JIALessonContent?
    @State private var loading = true
    @State private var errorMessage: String?

    public init(lesson: JIALessonSummary, client: JIAAPIClient) {
        self.lesson = lesson
        self.client = client
    }

    public var body: some View {
        Group {
            if loading { ProgressView("Loading lesson") }
            else if let content { JIALessonReaderView(content: content) }
            else if let errorMessage { ContentUnavailableView("Unable to load lesson", systemImage: "exclamationmark.triangle", description: Text(errorMessage)) }
            else { ContentUnavailableView("Lesson content unavailable", systemImage: "doc.text") }
        }
        .navigationTitle(lesson.title)
        .task { await load() }
    }

    private func load() async {
        loading = true
        defer { loading = false }
        do {
            let response: JIAFullLesson = try await client.request("/api/v1/lessons/\(lesson.slug)")
            guard let raw = response.contentJson, let data = raw.data(using: .utf8) else {
                throw JIAAPIError.invalidResponse
            }
            content = try JSONDecoder().decode(JIALessonContent.self, from: data)
            errorMessage = nil
        } catch { errorMessage = error.localizedDescription }
    }
}
