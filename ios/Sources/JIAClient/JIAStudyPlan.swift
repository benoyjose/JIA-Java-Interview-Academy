import Foundation
import SwiftUI

public struct JIAStudyPlanTask: Codable, Sendable, Equatable, Identifiable {
    public let kind: String
    public let title: String
    public let lessonSlug: String?
    public let topicSlug: String?
    public let minutes: Int
    public let reason: String
    public var id: String { (lessonSlug ?? topicSlug ?? title) + ":" + kind }
}
public struct JIAStudyPlanResponse: Codable, Sendable, Equatable {
    public let dailyMinutes: Int
    public let tasks: [JIAStudyPlanTask]
    public var allocatedMinutes: Int { tasks.reduce(0) { $0 + max(0, $1.minutes) } }
}
public enum JIAStudyPlanBudget {
    public static func clamp(_ minutes: Int) -> Int { min(180, max(10, minutes)) }
    public static func allocate(_ tasks: [JIAStudyPlanTask], minutes: Int) -> [JIAStudyPlanTask] {
        var remaining = clamp(minutes)
        return tasks.compactMap { task in
            guard remaining > 0 else { return nil }
            let allotted = min(max(1, task.minutes), remaining)
            remaining -= allotted
            return JIAStudyPlanTask(kind: task.kind, title: task.title, lessonSlug: task.lessonSlug,
                topicSlug: task.topicSlug, minutes: allotted, reason: task.reason)
        }
    }
}

public struct JIAStudyPlanView: View {
    private let client: JIAAPIClient
    @ObservedObject private var session: JIAAuthSession
    @State private var plan: JIAStudyPlanResponse?
    @State private var isLoading = false
    @State private var errorMessage: String?
    public init(client: JIAAPIClient, session: JIAAuthSession) {
        self.client = client; self.session = session
    }
    public var body: some View {
        List {
            if !session.isSignedIn {
                Text("Sign in to build a plan from your lesson progress and quiz accuracy.")
            } else if let plan {
                Section("Today · " + String(plan.dailyMinutes) + " minute target") {
                    if plan.tasks.isEmpty { ContentUnavailableView("You're on track", systemImage: "checkmark.seal",
                        description: Text("No unfinished lessons or weak topics were returned.")) }
                    ForEach(plan.tasks) { task in
                        VStack(alignment: .leading, spacing: 5) {
                            Text(task.title).font(.headline)
                            Text(String(task.minutes) + " minutes").font(.caption).foregroundStyle(.secondary)
                            Text(task.reason).font(.subheadline)
                        }.padding(.vertical, 4)
                    }
                }
            }
            if let errorMessage { Section { Text(errorMessage).foregroundStyle(.red) } }
            Section { Button(isLoading ? "Refreshing…" : "Refresh study plan") { Task { await load() } }
                .disabled(isLoading || !session.isSignedIn) }
        }
        .navigationTitle("Study plan")
        .task { await session.ensureAccessToken(); await load() }
    }
    @MainActor private func load() async {
        guard let token = session.accessToken else { errorMessage = "Sign in to load your study plan."; return }
        isLoading = true; defer { isLoading = false }
        do {
            let response: JIAStudyPlanResponse = try await client.request("/api/v1/me/study-plan?dailyMinutes=30", token: token)
            plan = JIAStudyPlanResponse(dailyMinutes: JIAStudyPlanBudget.clamp(response.dailyMinutes),
                tasks: JIAStudyPlanBudget.allocate(response.tasks, minutes: response.dailyMinutes))
            errorMessage = nil
        } catch { errorMessage = "Study plan unavailable: " + error.localizedDescription }
    }
}
