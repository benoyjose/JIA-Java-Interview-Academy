import SwiftUI

public struct JIAQuizView: View {
    private let client: JIAAPIClient
    @ObservedObject private var session: JIAAuthSession
    @State private var selectedMode: JIAQuizMode = .practice
    @State private var difficulty = "PROFESSIONAL"
    @State private var questionCount = 10
    @State private var durationSeconds = 1800
    @State private var remainingSeconds: Int?
    @State private var attempt: JIAQuizAttemptResponse?
    @State private var answers: [Int: Set<String>] = [:]
    @State private var result: JIAQuizResultResponse?
    @State private var history: [JIAQuizHistoryItem] = []
    @State private var loading = false
    @State private var errorMessage: String?

    public init(client: JIAAPIClient, session: JIAAuthSession) {
        self.client = client
        self.session = session
    }

    public var body: some View {
        Form {
            if !session.isSignedIn {
                Section {
                    Text("Sign in to start a quiz and save your assessment history.")
                    NavigationLink("Open account", destination: JIAAccountView(session: session))
                }
            } else if let result, let attempt {
                Section("Result") {
                    Text("\(result.correctCount) correct · \(result.gradedQuestionCount) auto-graded · \(result.selfReviewQuestionCount) self-review")
                        .font(.headline)
                    if result.gradedQuestionCount > 0 {
                        Text("Objective score: \(result.scorePercent, specifier: "%.1f")%")
                    } else {
                        Text("No automatic score for this attempt; compare your answers with the rubric.")
                    }
                    ForEach(result.answers) { answer in
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Question \(answer.position)")
                                .font(.subheadline.bold())
                            if answer.requiresSelfReview {
                                Text("Self-review required")
                                    .foregroundStyle(.orange)
                            } else {
                                Text(answer.isCorrect == true ? "Correct" : "Incorrect")
                                    .foregroundStyle(answer.isCorrect == true ? .green : .red)
                            }
                            if let explanation = answer.explanation, !explanation.isEmpty {
                                Text(explanation).font(.footnote)
                            }
                        }
                    }
                    Button("New quiz") {
                        self.result = nil
                        self.attempt = nil
                        self.answers = [:]
                    }
                }
            } else if let attempt {
                Section("Assessment · \(attempt.mode.title)") {
                    if let remainingSeconds {
                        Text("Time remaining: \(remainingSeconds) seconds")
                    }
                    Text("\(answers.count) of \(attempt.questions.count) answered")
                }
                ForEach(attempt.questions) { question in
                    Section("Q\(question.position) · \(question.questionType.replacingOccurrences(of: "_", with: " "))") {
                        Text(question.prompt.text ?? "Question prompt")
                        if let code = question.prompt.code?.source, !code.isEmpty {
                            ScrollView(.horizontal) {
                                Text(code).font(.system(.body, design: .monospaced))
                                    .textSelection(.enabled)
                            }
                        }
                        if question.requiresTextResponse {
                            TextEditor(text: responseBinding(for: question.position))
                                .frame(minHeight: 130)
                            Text("This answer is reviewed against the explanation/rubric; it is not exact-string graded.")
                                .font(.caption).foregroundStyle(.secondary)
                        } else {
                            ForEach(question.options ?? []) { option in
                                let selected = answers[question.position]?.contains(option.value) == true
                                Button {
                                    updateSelection(option.value, for: question)
                                } label: {
                                    HStack {
                                        Text(option.label)
                                        Spacer()
                                        if selected { Image(systemName: "checkmark.circle.fill") }
                                    }
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        if let hint = question.hint, !hint.isEmpty {
                            Text("Hint: \(hint)").font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                }
                Section {
                    Button(loading ? "Submitting…" : "Submit answers") {
                        Task { await submit() }
                    }
                    .disabled(loading || (attempt.mode != .timedMock && answers.count < attempt.questions.count))
                    Button("Abandon attempt", role: .destructive) {
                        Task { await abandon() }
                    }
                    .disabled(loading)
                }
            } else {
                Section("Quiz setup") {
                    Picker("Mode", selection: $selectedMode) {
                        ForEach(JIAQuizMode.allCases) { mode in Text(mode.title).tag(mode) }
                    }
                    Picker("Difficulty", selection: $difficulty) {
                        ForEach(["BEGINNER", "NOVICE", "PROFESSIONAL", "EXPERT"], id: \.self) { level in
                            Text(level.capitalized).tag(level)
                        }
                    }
                    Picker("Questions", selection: $questionCount) {
                        ForEach([5, 10, 20], id: \.self) { count in Text("\(count)").tag(count) }
                    }
                    if selectedMode == .timedMock {
                        Picker("Duration", selection: $durationSeconds) {
                            Text("15 minutes").tag(900)
                            Text("30 minutes").tag(1800)
                            Text("60 minutes").tag(3600)
                        }
                    }
                    Button(loading ? "Starting…" : "Start \(selectedMode.title.lowercased())") {
                        Task { await start() }
                    }
                    .disabled(loading || !session.isSignedIn)
                }
                Section("Recent attempts") {
                    Button("Load history") { Task { await loadHistory() } }
                    ForEach(history) { item in
                        VStack(alignment: .leading) {
                            Text("\(item.mode.title) · \(item.difficulty)")
                            Text("\(item.correctCount ?? 0)/\(item.questionCount) · \(item.scorePercent.map { String(format: "%.1f%%", $0) } ?? item.status)")
                                .font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                }
            }
            if let errorMessage {
                Section { Text(errorMessage).foregroundStyle(.red) }
            }
        }
        .navigationTitle("Quiz & Mock Interview")
        .task { await session.refreshIfNeeded() }
        .task(id: attempt?.deadlineAt) {
            guard let current = attempt, current.mode == .timedMock,
                  let deadline = parsedDeadline(current.deadlineAt) else {
                remainingSeconds = nil
                return
            }
            while result == nil && !Task.isCancelled {
                let remaining = max(0, Int(ceil(deadline.timeIntervalSinceNow)))
                remainingSeconds = remaining
                if remaining == 0 {
                    await submit()
                    return
                }
                try? await Task.sleep(for: .seconds(1))
            }
        }
    }

    private func responseBinding(for position: Int) -> Binding<String> {
        Binding(
            get: { answers[position]?.first ?? "" },
            set: { value in answers[position] = value.isEmpty ? [] : [value] }
        )
    }

    private func updateSelection(_ value: String, for question: JIAQuizQuestion) {
        if question.allowsMultipleSelection {
            var current = answers[question.position] ?? []
            if current.contains(value) { current.remove(value) } else { current.insert(value) }
            answers[question.position] = current
        } else {
            answers[question.position] = [value]
        }
    }

    private func start() async {
        guard let token = session.accessToken else { errorMessage = "Please sign in."; return }
        loading = true
        errorMessage = nil
        defer { loading = false }
        do {
            let body = StartRequest(difficulty: difficulty, questionCount: questionCount,
                mode: selectedMode, durationSeconds: selectedMode == .timedMock ? durationSeconds : nil)
            let data = try JSONEncoder().encode(body)
            let started: JIAQuizAttemptResponse = try await client.request(
                "/api/v1/quiz-attempts", token: token, method: "POST", body: data)
            attempt = started
            answers = [:]
            result = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func submit() async {
        guard let token = session.accessToken, let attempt else { return }
        loading = true
        errorMessage = nil
        defer { loading = false }
        do {
            let rows = attempt.questions.compactMap { question -> [String: Any]? in
                let values = Array(answers[question.position] ?? []).sorted()
                guard !values.isEmpty else { return nil }
                let response: [String: Any] = values.count > 1 ? ["values": values] : ["value": values.first ?? ""]
                return ["position": question.position, "response": response]
            }
            let body = try JSONSerialization.data(withJSONObject: ["answers": rows])
            let submitted: JIAQuizResultResponse = try await client.request(
                "/api/v1/quiz-attempts/\(attempt.attemptId)/submit", token: token, method: "POST", body: body)
            result = submitted
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func abandon() async {
        guard let token = session.accessToken, let attempt else { return }
        loading = true
        defer { loading = false }
        do {
            let _: EmptyResponse = try await client.request(
                "/api/v1/quiz-attempts/\(attempt.attemptId)/abandon", token: token, method: "POST",
                body: Data("{}".utf8))
            self.attempt = nil
            answers = [:]
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func loadHistory() async {
        guard let token = session.accessToken else { return }
        do {
            history = try await client.request("/api/v1/me/quiz-attempts", token: token)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func parsedDeadline(_ value: String?) -> Date? {
        guard let value else { return nil }
        return ISO8601DateFormatter().date(from: value)
    }

    private struct StartRequest: Encodable {
        let difficulty: String
        let questionCount: Int
        let mode: JIAQuizMode
        let durationSeconds: Int?
    }
    private struct EmptyResponse: Decodable {}
}
