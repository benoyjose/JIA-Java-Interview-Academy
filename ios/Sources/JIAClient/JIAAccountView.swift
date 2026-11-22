import SwiftUI

public struct JIAAccountView: View {
    @ObservedObject private var session: JIAAuthSession
    @State private var email = ""
    @State private var password = ""
    @State private var isSubmitting = false

    public init(session: JIAAuthSession) {
        self.session = session
    }

    public var body: some View {
        Form {
            if session.isRestoring {
                ProgressView("Restoring session…")
            }
            if let user = session.user {
                Section("Signed in") {
                    LabeledContent("Name", value: user.name)
                    LabeledContent("Email", value: user.email)
                    Text(user.roles.joined(separator: ", ")).foregroundStyle(.secondary)
                    Button("Sign out", role: .destructive) {
                        Task { await session.signOut() }
                    }
                }
            } else {
                Section("Sign in to JIA") {
                    TextField("Email", text: $email)
                        .textContentType(.emailAddress)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    SecureField("Password", text: $password)
                        .textContentType(.password)
                    Button(isSubmitting ? "Signing in…" : "Sign in") {
                        isSubmitting = true
                        Task {
                            await session.signIn(email: email, password: password)
                            isSubmitting = false
                        }
                    }
                    .disabled(isSubmitting || email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || password.isEmpty)
                }
            }
            if let error = session.errorMessage {
                Section {
                    Text(error).foregroundStyle(.red)
                }
            }
            Section {
                Text("JIA uses the same account and backend identity across supported platforms. Public lessons remain available without signing in.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Account")
    }
}
