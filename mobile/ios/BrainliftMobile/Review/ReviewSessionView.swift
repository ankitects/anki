// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import SwiftUI

struct ReviewSessionView: View {
    @StateObject private var model: ReviewSessionViewModel
    @StateObject private var scoreModel: ScorePanelViewModel
    @StateObject private var syncCoordinator: SyncCoordinator
    @State private var showSync = false
    private let credentialsStore: SyncCredentialsStore

    init(backend: any CompanionBackend = AnkiBackend()) {
        let credentialsStore = SyncCredentialsStore()
        self.credentialsStore = credentialsStore
        _model = StateObject(
            wrappedValue: ReviewSessionViewModel(backend: backend)
        )
        _scoreModel = StateObject(
            wrappedValue: ScorePanelViewModel(backend: backend)
        )
        _syncCoordinator = StateObject(
            wrappedValue: SyncCoordinator(
                backend: backend,
                credentials: credentialsStore
            )
        )
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                ScorePanelView(model: scoreModel)
                Group {
                    switch model.phase {
                    case .loading:
                        ProgressView("Opening collection…")
                    case .choosingDeck:
                        DeckPickerView(decks: model.decks) { deck in
                            Task {
                                await model.selectDeck(deck)
                                await scoreModel.refresh()
                            }
                        }
                        .frame(minHeight: 320)
                    case .question, .answer:
                        card.frame(minHeight: 360)
                    case .finished:
                        ContentUnavailableView(
                            "Session complete",
                            systemImage: "checkmark.circle",
                            description: Text("No cards are due in this deck.")
                        )
                    case .error:
                        error
                    }
                }
            }
            .padding()
        }
        .navigationTitle(model.selectedDeck?.name ?? "Brainlift")
        .toolbar {
            Button("Sync", systemImage: "arrow.triangle.2.circlepath") {
                showSync = true
            }
            .accessibilityIdentifier("open-sync")
            if model.canUndo {
                Button("Undo", systemImage: "arrow.uturn.backward") {
                    Task { await model.undo() }
                }
                .accessibilityIdentifier("undo-review")
            }
        }
        .task {
            guard model.phase == .loading else { return }
            syncCoordinator.setCompletion {
                await scoreModel.refresh()
            }
            await model.start()
            await scoreModel.refresh()
        }
        .sheet(isPresented: $showSync) {
            SyncSettingsView(
                coordinator: syncCoordinator,
                credentialsStore: credentialsStore
            )
        }
    }

    private var card: some View {
        VStack(spacing: 16) {
            CardWebView(html: model.displayedHTML)
                .accessibilityIdentifier("review-card")

            if model.phase == .question {
                Button("Show Answer") {
                    model.revealAnswer()
                }
                .buttonStyle(.borderedProminent)
                .accessibilityIdentifier("show-answer")
            } else {
                HStack {
                    ForEach(ReviewRating.allCases) { rating in
                        gradeButton(rating)
                    }
                }
            }
        }
        .padding()
    }

    private func gradeButton(_ rating: ReviewRating) -> some View {
        let identifier = "grade-\(rating.rawValue.lowercased())"
        return Button(rating.rawValue) {
            Task {
                await model.grade(rating)
                await scoreModel.refresh()
            }
        }
        .buttonStyle(.bordered)
        .tint(rating == .good ? .accentColor : .secondary)
        .disabled(!model.canGrade)
        .accessibilityIdentifier(identifier)
    }

    private var error: some View {
        ContentUnavailableView {
            Label("Unable to show card", systemImage: "exclamationmark.triangle")
        } description: {
            Text(model.errorMessage ?? "An unknown rendering error occurred.")
        } actions: {
            if model.canRetry {
                Button("Try Again") {
                    Task { await model.retry() }
                }
                .accessibilityIdentifier("retry-review")
            }
        }
    }
}
