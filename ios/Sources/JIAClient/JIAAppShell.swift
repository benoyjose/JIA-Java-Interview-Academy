import SwiftUI

@MainActor
public struct JIAAppShell: View {
    private let client: JIAAPIClient
    @State private var technologies: [JIATechnology] = []
    @State private var isLoading = false
    @State private var errorMessage: String?

    public init(client: JIAAPIClient) { self.client = client }

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
                        VStack(alignment: .leading, spacing: 4) {
                            Text(technology.name).font(.headline)
                            if let description = technology.description {
                                Text(description).font(.subheadline).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
            .navigationTitle("JIA Academy")
            .task { await load() }
            .refreshable { await load() }
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
