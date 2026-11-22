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
    @ObservedObject var session: JIAAuthSession
    @State private var categories: [JIACategory] = []
    @State private var loading = true
    @State private var errorMessage: String?

    public init(technology: JIATechnology, client: JIAAPIClient, session: JIAAuthSession) {
        self.technology = technology
        self.client = client
        self.session = session
    }

    public var body: some View {
        List {
            if loading { ProgressView("Loading categories") }
            if let errorMessage {
                VStack(alignment: .leading, spacing: 8) {
                    Text(errorMessage).foregroundStyle(.red)
                    Button("Retry") { Task { await load() } }
                }
            }
            ForEach(categories) { category in
                NavigationLink(category.name) {
                    JIALessonListView(category: category, client: client, session: session)
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
    @ObservedObject var session: JIAAuthSession
    @State private var lessons: [JIALessonSummary] = []
    @State private var loading = true
    @State private var errorMessage: String?

    public init(category: JIACategory, client: JIAAPIClient, session: JIAAuthSession) {
        self.category = category
        self.client = client
        self.session = session
    }

    public var body: some View {
        List {
            if loading { ProgressView("Loading lessons") }
            if let errorMessage {
                VStack(alignment: .leading, spacing: 8) {
                    Text(errorMessage).foregroundStyle(.red)
                    Button("Retry") { Task { await load() } }
                }
            }
            ForEach(lessons) { lesson in
                NavigationLink {
                    JIALessonDetailView(lesson: lesson, client: client, session: session)
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
    @ObservedObject var session: JIAAuthSession
    @State private var content: JIALessonContent?
    @State private var loading = true
    @State private var errorMessage: String?
    @State private var isBookmarked = false
    @State private var savedProgress = 0.0
    @State private var progressToSave = 0.0

    public init(lesson: JIALessonSummary, client: JIAAPIClient, session: JIAAuthSession) {
        self.lesson = lesson
        self.client = client
        self.session = session
    }

    public var body: some View {
        Group {
            if loading { ProgressView("Loading lesson") }
            else if let content { JIALessonReaderView(content: content) }
            else if let errorMessage { ContentUnavailableView("Unable to load lesson", systemImage: "exclamationmark.triangle", description: Text(errorMessage)) }
            else { ContentUnavailableView("Lesson content unavailable", systemImage: "doc.text") }
        }
        .navigationTitle(lesson.title)
        .task { await load(); await loadLearnerState() }
        .toolbar {
            if session.isSignedIn {
                ToolbarItem(placement: .automatic) {
                    Button { Task { await toggleBookmark() } } label: {
                        Label(isBookmarked ? "Remove bookmark" : "Bookmark lesson",
                              systemImage: isBookmarked ? "bookmark.fill" : "bookmark")
                    }
                }
            }
        }
        .safeAreaInset(edge: .bottom) {
            if session.isSignedIn {
                HStack {
                    Text("Progress")
                    Slider(value: $progressToSave, in: 0...100, step: 5)
                    Text("\(Int(progressToSave))%")
                    Button("Save") { Task { await saveProgress() } }
                }.padding().background(.regularMaterial)
            }
        }
    }

    private func loadLearnerState() async {
        await session.refreshIfNeeded()
        guard let token = session.accessToken else { return }
        do {
            let progress: [JIALessonProgress] = try await client.request(
                "/api/v1/me/lesson-progress", token: token)
            if let item = progress.first(where: { $0.lessonSlug == lesson.slug }) {
                savedProgress = item.progressPercent
                progressToSave = item.progressPercent
            }
            let bookmarks: [JIABookmark] = try await client.request(
                "/api/v1/me/bookmarks", token: token)
            isBookmarked = bookmarks.contains { $0.lessonSlug == lesson.slug }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func saveProgress() async {
        await session.refreshIfNeeded()
        guard let token = session.accessToken else { errorMessage = "Please sign in."; return }
        do {
            let payload = ProgressUpdate(contentRevision: lesson.contentRevision,
                progressPercent: progressToSave,
                lastPosition: ["source": .string("ios-reader-slider")])
            let body = try JSONEncoder().encode(payload)
            _ = try await client.perform("/api/v1/me/lesson-progress/\(lesson.slug)",
                token: token, method: "PUT", body: body)
            savedProgress = progressToSave
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func toggleBookmark() async {
        await session.refreshIfNeeded()
        guard let token = session.accessToken else { errorMessage = "Please sign in."; return }
        do {
            if isBookmarked {
                _ = try await client.perform("/api/v1/me/bookmarks/\(lesson.slug)",
                    token: token, method: "DELETE")
            } else {
                _ = try await client.perform("/api/v1/me/bookmarks/\(lesson.slug)",
                    token: token, method: "PUT")
            }
            isBookmarked.toggle()
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private struct ProgressUpdate: Encodable {
        let contentRevision: Int
        let progressPercent: Double
        let lastPosition: [String: JIAJSONValue]
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
