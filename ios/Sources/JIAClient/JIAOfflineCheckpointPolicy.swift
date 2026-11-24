import Foundation

public struct OfflineCheckpoint: Equatable, Sendable {
    public let lessonID: String
    public let position: Int
    public let sequence: Int
    public let recordedAt: Date

    public init(lessonID: String, position: Int, sequence: Int, recordedAt: Date) {
        self.lessonID = lessonID
        self.position = position
        self.sequence = sequence
        self.recordedAt = recordedAt
    }
}

public enum OfflineCheckpointPolicy {
    /// Accept a checkpoint only when it advances position/sequence and the debounce window elapsed.
    public static func shouldPersist(
        previous: OfflineCheckpoint?,
        proposed: OfflineCheckpoint,
        minimumInterval: TimeInterval
    ) -> Bool {
        guard !proposed.lessonID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              proposed.position >= 0, proposed.sequence >= 0, minimumInterval >= 0 else { return false }
        guard let previous else { return true }
        guard previous.lessonID == proposed.lessonID,
              proposed.sequence > previous.sequence,
              proposed.position >= previous.position else { return false }
        return proposed.recordedAt.timeIntervalSince(previous.recordedAt) >= minimumInterval
    }
}
