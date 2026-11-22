import Foundation

public enum JIAQuizMode: String, CaseIterable, Codable, Sendable, Identifiable {
    case practice = "PRACTICE"
    case timedMock = "TIMED_MOCK"
    case dailyChallenge = "DAILY_CHALLENGE"
    case weakArea = "WEAK_AREA"

    public var id: String { rawValue }
    public var title: String {
        switch self {
        case .practice: "Practice"
        case .timedMock: "Timed mock"
        case .dailyChallenge: "Daily challenge"
        case .weakArea: "Weak areas"
        }
    }
}

public struct JIAQuizOption: Codable, Sendable, Equatable, Identifiable {
    public let value: String
    public let label: String
    public var id: String { value }
}

public struct JIAQuizCode: Codable, Sendable, Equatable {
    public let language: String?
    public let source: String?
}

public struct JIAQuizPrompt: Codable, Sendable, Equatable {
    public let text: String?
    public let code: JIAQuizCode?
}

public struct JIAQuizQuestion: Codable, Sendable, Equatable, Identifiable {
    public let position: Int
    public let questionType: String
    public let prompt: JIAQuizPrompt
    public let options: [JIAQuizOption]?
    public let hint: String?
    public var id: Int { position }

    public var requiresTextResponse: Bool {
        ["OUTPUT_PREDICTION", "DEBUGGING", "CODING", "SQL", "SCENARIO"].contains(questionType)
    }
    public var allowsMultipleSelection: Bool { questionType == "MULTI_SELECT" }
}

public struct JIAQuizAttemptResponse: Codable, Sendable, Equatable {
    public let attemptId: UUID
    public let difficulty: String
    public let mode: JIAQuizMode
    public let questionCount: Int
    public let deadlineAt: String?
    public let durationLimitSeconds: Int?
    public let questions: [JIAQuizQuestion]
}

public struct JIAQuizAnswerResult: Codable, Sendable, Equatable, Identifiable {
    public let position: Int
    public let isCorrect: Bool?
    public let requiresSelfReview: Bool
    public let explanation: String?
    public var id: Int { position }
}

public struct JIAQuizResultResponse: Codable, Sendable, Equatable {
    public let attemptId: UUID
    public let questionCount: Int
    public let correctCount: Int
    public let gradedQuestionCount: Int
    public let selfReviewQuestionCount: Int
    public let scorePercent: Double
    public let timedOut: Bool
    public let answers: [JIAQuizAnswerResult]
}

public struct JIAQuizHistoryResponse: Codable, Sendable, Equatable {
    public let attempts: [JIAQuizHistoryItem]
}

public struct JIAQuizHistoryItem: Codable, Sendable, Equatable, Identifiable {
    public let attemptId: UUID
    public let difficulty: String
    public let mode: JIAQuizMode
    public let questionCount: Int
    public let correctCount: Int?
    public let scorePercent: Double?
    public let status: String
    public var id: UUID { attemptId }
    enum CodingKeys: String, CodingKey {
        case attemptId = "id"
        case difficulty, mode, questionCount, correctCount, scorePercent, status
    }
}
