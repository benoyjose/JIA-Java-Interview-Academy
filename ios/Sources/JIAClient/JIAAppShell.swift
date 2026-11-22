import SwiftUI

@MainActor
public struct JIAAppShell: View {
    private let client: JIAAPIClient
    private let offlineStore: JIAOfflineContentStore
    @State private var isSyncingOffline = false
    @State private var offlineSyncMessage: String?
    @State private var technologies: [JIATechnology] = []
    @State private var isLoading = false
    @State private var errorMessage: String?
    @StateObject private var authSession: JIAAuthSession

    public init(client: JIAAPIClient) {
        self.client = client
        self.offlineStore = JIAOfflineContentStore()
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
                            JIACategoryListView(technology: technology, client: client, session: authSession, offlineStore: offlineStore)
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
                            do {
                                _ = try await service.syncLatestRelease()
                                offlineSyncMessage = service.statusMessage
                            } catch {
                                offlineSyncMessage = "Offline download failed. The previous release remains active: \(error.localizedDescription)"
                            }
                            isSyncingOffline = false
                        }
                    } label: {
                        Label(isSyncingOffline ? "Downloading…" : "Offline",
                              systemImage: isSyncingOffline ? "arrow.down.circle" : "arrow.down.to.line")
                    }
                    .disabled(isSyncingOffline)
                }
            }
            .task { await authSession.restoreSession() }
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
