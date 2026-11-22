import Foundation
import Combine
#if canImport(Security)
import Security
#endif

public struct JIAAuthenticatedUser: Codable, Sendable, Equatable {
    public let id: UUID
    public let name: String
    public let email: String
    public let roles: [String]
}

public struct JIATokenResponse: Codable, Sendable, Equatable {
    public let accessToken: String
    public let refreshToken: String
    public let tokenType: String
    public let expiresAt: String
    public let user: JIAAuthenticatedUser
}

public protocol JIARefreshTokenStore: Sendable {
    func load() -> String?
    func save(_ token: String)
    func clear()
}

/// Stores only the rotating refresh token in Keychain. Short-lived access tokens stay in memory.
public struct JIAKeychainRefreshTokenStore: JIARefreshTokenStore {
    private let service = "com.jia.academy.auth"
    private let account = "refresh-token"

    public init() {}

    public func load() -> String? {
        #if canImport(Security)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
        #else
        return nil
        #endif
    }

    public func save(_ token: String) {
        #if canImport(Security)
        let key: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        let data = Data(token.utf8)
        let update = [kSecValueData as String: data]
        if SecItemUpdate(key as CFDictionary, update as CFDictionary) == errSecItemNotFound {
            var insert = key
            insert[kSecValueData as String] = data
            SecItemAdd(insert as CFDictionary, nil)
        }
        #endif
    }

    public func clear() {
        #if canImport(Security)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
        #endif
    }
}

@MainActor
public final class JIAAuthSession: ObservableObject {
    @Published public private(set) var user: JIAAuthenticatedUser?
    @Published public private(set) var accessToken: String?
    @Published public private(set) var expiresAt: Date?
    @Published public private(set) var isRestoring = false
    @Published public private(set) var errorMessage: String?

    private let client: JIAAPIClient
    private let tokenStore: JIARefreshTokenStore
    private let decoder = JSONDecoder()

    public init(client: JIAAPIClient, tokenStore: JIARefreshTokenStore = JIAKeychainRefreshTokenStore()) {
        self.client = client
        self.tokenStore = tokenStore
    }

    public var isSignedIn: Bool { accessToken != nil && user != nil }
    public var canUseLearnerFeatures: Bool { isSignedIn || !(tokenStore.load() ?? "").isEmpty }

    public func signIn(email: String, password: String) async {
        errorMessage = nil
        do {
            let body = try JSONEncoder().encode(LoginRequest(email: email.trimmingCharacters(in: .whitespacesAndNewlines),
                                                            password: password, deviceName: "ios"))
            let response: JIATokenResponse = try await client.request("/api/v1/auth/login", method: "POST", body: body)
            accept(response)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    public func restoreSession() async {
        guard accessToken == nil, !isRestoring,
              let refreshToken = tokenStore.load(), !refreshToken.isEmpty else { return }
        isRestoring = true
        defer { isRestoring = false }
        do {
            let body = try JSONEncoder().encode(RefreshRequest(refreshToken: refreshToken))
            let response: JIATokenResponse = try await client.request("/api/v1/auth/refresh", method: "POST", body: body)
            accept(response)
        } catch let error as JIAAPIError {
            if case .httpStatus(let status) = error, status == 401 || status == 403 {
                signOutLocally()
            }
            // A transport failure is an offline state, not proof that the refresh token is invalid.
            errorMessage = nil
        } catch {
            errorMessage = nil
        }
    }

    public func refreshIfNeeded() async {
        guard let expiresAt, expiresAt.timeIntervalSinceNow < 60,
              let refreshToken = tokenStore.load(), !refreshToken.isEmpty else { return }
        do {
            let body = try JSONEncoder().encode(RefreshRequest(refreshToken: refreshToken))
            let response: JIATokenResponse = try await client.request("/api/v1/auth/refresh", method: "POST", body: body)
            accept(response)
        } catch let error as JIAAPIError {
            if case .httpStatus(let status) = error, status == 401 || status == 403 {
                signOutLocally()
                errorMessage = "Your session expired. Please sign in again."
            }
            // Preserve the refresh token on network/server failures so queued changes survive offline launches.
        } catch {
            // Keep local credentials and queued work; retry when connectivity returns.
        }
    }

    public func signOut() async {
        if let accessToken {
            _ = try? await client.perform("/api/v1/auth/logout", token: accessToken, method: "POST")
        }
        signOutLocally()
    }

    private func accept(_ response: JIATokenResponse) {
        accessToken = response.accessToken
        user = response.user
        expiresAt = ISO8601DateFormatter().date(from: response.expiresAt)
        tokenStore.save(response.refreshToken)
    }

    private func signOutLocally() {
        tokenStore.clear()
        accessToken = nil
        user = nil
        expiresAt = nil
    }

    private struct LoginRequest: Encodable {
        let email: String
        let password: String
        let deviceName: String
    }
    private struct RefreshRequest: Encodable { let refreshToken: String }
    private struct EmptyResponse: Decodable {}
}
