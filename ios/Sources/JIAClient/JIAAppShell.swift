import SwiftUI

@MainActor
public struct JIAAppShell: View {
    private let client: JIAAPIClient
    private let offlineStore: JIAOfflineContentStore
    private let offlineActivityQueue: JIAOfflineActivityQueue
    @State private var isSyncingOffline = false
    @State private var offlineSyncMessage: String?
    @State private var technologies: [JIATechnology] = []
    @State private var isLoading = false
    @State private var errorMessage: String?
    @StateObject private var authSession: JIAAuthSession

    public init(client: JIAAPIClient) {
        self.client = client
        self.offlineStore = JIAOfflineContentStore()
        self.offlineActivityQueue = JIAOfflineActivityQueue()
        _authSession = StateObject(wrappedValue: JIAAuthSession(client: client))
    }

    public var body: some View {
        NavigationStack {
            Group {
                if isLoading {
                    ProgressView("Loading learning tracks")
                } else if let errorMessage {
                    ContentUnavailableView("Unable to load JIA", systemImage: "wifi.exclamationmark",
                        description: Text(errorMessage))
                } else if technologies.isEmpty {
                    ContentUnavailableView("No learning tracks yet", systemImage: "books.vertical",
                        description: Text("Published tracks will appear here."))
                } else {
                    List(technologies) { technology in
                        NavigationLink {
                            JIACategoryListView(technology: technology, client: client, session: authSession, offlineStore: offlineStore, offlineActivityQueue: offlineActivityQueue)
                        } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(technology.name).font(.headline)
                                if let description = technology.description {
                                    Text(description).font(.subheadline).foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("JIA Academy")
            .toolbar {
                ToolbarItem(placement: .automatic) {
                    NavigationLink { JIAAccountView(session: authSession) } label: {
                        Label("Account", systemImage: "person.crop.circle")
                    }
                }
                ToolbarItem(placement: .automatic) {
                    NavigationLink { JIAQuizView(client: client, session: authSession) } label: {
                        Label("Quiz", systemImage: "checklist")
                    }
                }
                ToolbarItem(placement: .automatic) {
                    Button {
                        isSyncingOffline = true
                        Task {
                            let service = JIAOfflineSyncService(client: client, store: offlineStore)
                            var messages: [String] = []
                            do {
                                _ = try await service.syncLatestRelease()
                                if let status = service.statusMessage { messages.append(status) }
                            } catch {
                                messages.append("Content download failed; prior release remains active: \(error.localizedDescription)")
                            }
                            await authSession.refreshIfNeeded()
                            if let token = authSession.accessToken {
                                let activity = JIAOfflineActivitySyncService(client: client, queue: offlineActivityQueue)
                                let report = await activity.syncPending(token: token)
                                messages.append("Synced \(report.progress) progress and \(report.bookmarks) bookmarks; \(report.failures) remain queued.")
                            } else if !offlineActivityQueue.pendingProgress().isEmpty ||
                                        !offlineActivityQueue.pendingBookmarks().isEmpty {
                                messages.append("Learner activity remains queued until sign-in.")
                            }
                            offlineSyncMessage = messages.joined(separator: " ")
                            isSyncingOffline = false
                        }
                    } label: {
                        Label(isSyncingOffline ? "Downloading…" : "Offline",
                              systemImage: isSyncingOffline ? "arrow.down.circle" : "arrow.down.to.line")
                    }
                    .disabled(isSyncingOffline)
                }
            }
            .task {
                await authSession.restoreSession()
                await authSession.refreshIfNeeded()
                if let token = authSession.accessToken {
                    let activity = JIAOfflineActivitySyncService(client: client, queue: offlineActivityQueue)
                    _ = await activity.syncPending(token: token)
                }
            }
            .task { await load() }
            .refreshable { await load() }
            .overlay(alignment: .bottom) {
                if let offlineSyncMessage {
                    Text(offlineSyncMessage).font(.footnote).padding(12)
                        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 10))
                        .padding().onTapGesture { self.offlineSyncMessage = nil }
                }
            }
        }
    }

    private func load() async {
        isLoading = true
        defer { isLoading = false }
        do {
            technologies = try await client.request("/api/v1/technologies")
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
