// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import SwiftUI

struct SyncSettingsView: View {
    @ObservedObject var coordinator: SyncCoordinator
    let credentialsStore: SyncCredentialsStore
    @Environment(\.dismiss) private var dismiss
    @AppStorage("hasCompletedInitialSync") private var hasCompletedInitialSync = false
    @State private var username = ""
    @State private var password = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("Anki sync") {
                    TextField("Username", text: $username)
                        .textContentType(.username)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    SecureField("Password", text: $password)
                        .textContentType(.password)
                    Button("Save and Sync") {
                        Task { await saveAndSync() }
                    }
                    .disabled(username.isEmpty || password.isEmpty || isBusy)
                }

                if case .waitingForDirection(let directions) = coordinator.phase {
                    Section("Full sync direction") {
                        Text(coordinator.message)
                        ForEach(directions) { direction in
                            Button(
                                direction == .download
                                    ? "Download remote collection"
                                    : "Upload local collection"
                            ) {
                                Task {
                                    await coordinator.choose(direction)
                                    markCompletedIfNeeded()
                                }
                            }
                        }
                    }
                }

                if let progress = coordinator.progress {
                    Section("Progress") {
                        SyncProgressView(progress: progress)
                    }
                }

                if !coordinator.message.isEmpty {
                    Section("Status") {
                        Text(coordinator.message)
                    }
                }
            }
            .navigationTitle("Sync")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .task(id: isBusy) {
                while isBusy {
                    await coordinator.refreshProgress()
                    try? await Task.sleep(for: .milliseconds(500))
                }
            }
        }
    }

    private var isBusy: Bool {
        coordinator.phase == .authenticating || coordinator.phase == .syncing
    }

    private func saveAndSync() async {
        do {
            try credentialsStore.save(
                SyncCredentials(username: username, password: password)
            )
            await coordinator.sync(isCleanInstall: !hasCompletedInitialSync)
            markCompletedIfNeeded()
        } catch {
            coordinator.report(error)
        }
    }

    private func markCompletedIfNeeded() {
        if coordinator.phase == .completed {
            hasCompletedInitialSync = true
        }
    }
}
