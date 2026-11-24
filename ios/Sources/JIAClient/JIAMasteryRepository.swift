import Foundation

public struct JIAMasteryRepository: Sendable {
 private let client:JIAAPIClient
 public init(client:JIAAPIClient){self.client=client}
 public func load(token:String) async throws -> JIAMasteryResponse {
  try await client.request("/api/v1/me/mastery",token:token)
 }
}