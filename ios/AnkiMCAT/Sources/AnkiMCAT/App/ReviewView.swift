// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
//
// ReviewView — the single review screen. Renders the current card, a
// Show Answer button, then Again/Hard/Good/Easy grading buttons that round-trip
// through the shared Rust scheduler (C4). Kept intentionally plain — this is a
// functional review loop, not a designed UI.

import SwiftUI

struct ReviewView: View {
    // Received from the app; owns backend state and drives the loop.
    @Bindable var model: ReviewModel

    var body: some View {
        NavigationStack {
            Group {
                switch model.phase {
                case .launching:
                    launching
                case .reviewing:
                    reviewing
                case .finished:
                    finished
                case let .failed(message):
                    failure(message)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .navigationTitle("Anki MCAT")
        }
    }

    // MARK: - Phases

    private var launching: some View {
        VStack(spacing: 16) {
            ProgressView()
            Text(model.statusLine)
                .foregroundStyle(.secondary)
        }
        .accessibilityElement(children: .combine)
    }

    private var reviewing: some View {
        VStack(spacing: 0) {
            queueBar

            CardWebView(
                html: model.showingAnswer ? model.answerHTML : model.questionHTML,
                css: model.cardCSS
            )
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            Divider()

            if model.showingAnswer {
                gradingButtons
            } else {
                Button {
                    model.revealAnswer()
                } label: {
                    Text("Show Answer")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                }
                .buttonStyle(.borderedProminent)
                .padding()
                .accessibilityLabel("Show answer")
            }
        }
    }

    private var finished: some View {
        VStack(spacing: 16) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 56))
                .foregroundStyle(.green)
            Text("All caught up")
                .font(.title2)
            Text("Imported \(model.importedNotes) note(s).")
                .foregroundStyle(.secondary)
        }
        .accessibilityElement(children: .combine)
    }

    private func failure(_ message: String) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 48))
                .foregroundStyle(.orange)
            Text("Something went wrong")
                .font(.headline)
            Text(message)
                .font(.footnote)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
        }
    }

    // MARK: - Pieces

    private var queueBar: some View {
        HStack(spacing: 20) {
            countPill(model.newCount, color: .blue, label: "new")
            countPill(model.learningCount, color: .red, label: "learning")
            countPill(model.reviewCount, color: .green, label: "review")
        }
        .font(.subheadline.monospacedDigit())
        .padding(.vertical, 8)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(
            "\(model.newCount) new, \(model.learningCount) learning, \(model.reviewCount) review"
        )
    }

    private func countPill(_ value: UInt32, color: Color, label: String) -> some View {
        Text("\(value)")
            .foregroundStyle(color)
            .accessibilityLabel("\(value) \(label)")
    }

    private var gradingButtons: some View {
        HStack(spacing: 8) {
            gradeButton("Again", .again, .red)
            gradeButton("Hard", .hard, .orange)
            gradeButton("Good", .good, .green)
            gradeButton("Easy", .easy, .blue)
        }
        .padding()
    }

    private func gradeButton(
        _ title: String,
        _ rating: Anki_Scheduler_CardAnswer.Rating,
        _ color: Color
    ) -> some View {
        Button {
            Task { await model.answer(rating) }
        } label: {
            Text(title)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 6)
        }
        .buttonStyle(.bordered)
        .tint(color)
        .accessibilityLabel("Grade \(title)")
    }
}
