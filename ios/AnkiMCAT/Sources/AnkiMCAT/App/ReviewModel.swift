// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
//
// ReviewModel — the app's single source of truth. Drives the C3 (open +
// import) and C4 (review loop) flows through the shared Rust engine.
//
// Owns the AnkiEngine actor and exposes @MainActor UI state. All backend work
// hops onto the actor (serialized per the threading contract); results are
// published back on the main actor for SwiftUI to render.

import Foundation
import SwiftUI

/// High-level phase the UI renders.
enum ReviewPhase: Equatable {
    case launching          // opening backend / collection / importing
    case reviewing          // a card is on screen
    case finished           // queue empty — congrats
    case failed(String)     // a setup/RPC error to surface
}

@MainActor
@Observable
final class ReviewModel {
    // Engine is created once and reused; not observed (it's an actor).
    @ObservationIgnored private let engine = AnkiEngine()

    // UI state.
    private(set) var phase: ReviewPhase = .launching
    private(set) var statusLine: String = "Starting…"
    private(set) var importedNotes: UInt32 = 0

    // Current card being reviewed.
    private(set) var currentCard: Anki_Scheduler_QueuedCards.QueuedCard?
    private(set) var questionHTML: String = ""
    private(set) var answerHTML: String = ""
    private(set) var cardCSS: String = ""
    private(set) var showingAnswer: Bool = false

    // Remaining queue counts (for a lightweight progress readout).
    private(set) var newCount: UInt32 = 0
    private(set) var learningCount: UInt32 = 0
    private(set) var reviewCount: UInt32 = 0

    // MARK: - C3: launch → open backend, open/create collection, import apkg

    /// Full startup: open the backend, stage the bundled sample .apkg into the
    /// Documents sandbox, open/create a collection there, and import the apkg.
    func start() async {
        do {
            phase = .launching
            statusLine = "Opening backend…"
            try await engine.open(preferredLangs: ["en"])

            let paths = try sandboxPaths()
            let deck = try bundledDeck()
            // Import once: only (re)import when this deck hasn't been imported
            // yet, so we don't duplicate cards on every launch. Switching decks
            // (e.g. sample -> milesdown) starts from a clean collection.
            let needImport = importedDeckMarker() != deck.name
            if needImport {
                try clearCollection(paths)
            }
            statusLine = "Opening collection…"
            try await engine.openCollection(
                collectionPath: paths.collection,
                mediaFolderPath: paths.mediaFolder,
                mediaDbPath: paths.mediaDB
            )

            if needImport {
                statusLine = "Importing \(deck.name)…"
                let staged = try stageApkg(deck.url)
                importedNotes = try await engine.importAnkiPackage(packagePath: staged)
                try? FileManager.default.removeItem(atPath: staged)
                writeImportedDeckMarker(deck.name)
                statusLine = "Imported \(importedNotes) note(s)."
            }

            await advanceToNextCard()
        } catch {
            phase = .failed(String(describing: error))
            statusLine = "Error: \(error)"
        }
    }

    // MARK: - C4: review loop

    func revealAnswer() {
        showingAnswer = true
    }

    func answer(_ rating: Anki_Scheduler_CardAnswer.Rating) async {
        guard let card = currentCard else { return }
        do {
            _ = try await engine.answer(card: card, rating: rating)
            await advanceToNextCard()
        } catch {
            phase = .failed(String(describing: error))
            statusLine = "Answer failed: \(error)"
        }
    }

    /// Fetch the next queued card (or finish), and render its Q/A.
    private func advanceToNextCard() async {
        do {
            let queue = try await engine.queuedCards(fetchLimit: 1)
            newCount = queue.newCount
            learningCount = queue.learningCount
            reviewCount = queue.reviewCount

            guard let head = queue.cards.first else {
                currentCard = nil
                showingAnswer = false
                phase = .finished
                statusLine = "All caught up."
                return
            }

            let rendered = try await engine.render(cardID: head.card.id)
            currentCard = head
            questionHTML = rendered.question
            answerHTML = rendered.answer
            cardCSS = rendered.css
            showingAnswer = false
            phase = .reviewing
        } catch {
            phase = .failed(String(describing: error))
            statusLine = "Failed to load next card: \(error)"
        }
    }

    // MARK: - Sandbox / bundled resource staging

    private struct CollectionPaths {
        let collection: String
        let mediaFolder: String
        let mediaDB: String
    }

    /// Build (and ensure the directory for) the collection paths under the
    /// app's Documents sandbox. iOS storage guards (cfg(ios)) in rslib allow
    /// SQLite/WAL here.
    private func sandboxPaths() throws -> CollectionPaths {
        let fm = FileManager.default
        let docs = try fm.url(for: .documentDirectory, in: .userDomainMask,
                              appropriateFor: nil, create: true)
        let mediaFolder = docs.appendingPathComponent("collection.media", isDirectory: true)
        try fm.createDirectory(at: mediaFolder, withIntermediateDirectories: true)
        return CollectionPaths(
            collection: docs.appendingPathComponent("collection.anki2").path,
            mediaFolder: mediaFolder.path,
            mediaDB: docs.appendingPathComponent("collection.media.db2").path
        )
    }

    /// The bundled deck to study. Prefers the real MileDown deck (a
    /// media-stripped export, ~0.6 MB, bundled as milesdown.apkg) so the app
    /// shows real MCAT card content; falls back to the tiny built-in test deck
    /// (sample.apkg) if MileDown wasn't bundled. Images won't render (media was
    /// stripped to keep the bundle small), but all card text is present.
    private func bundledDeck() throws -> (url: URL, name: String) {
        if let u = Bundle.main.url(forResource: "milesdown", withExtension: "apkg") {
            return (u, "milesdown")
        }
        if let u = Bundle.main.url(forResource: "sample", withExtension: "apkg") {
            return (u, "sample")
        }
        throw AnkiEngineError.decode("no bundled .apkg found")
    }

    /// Copy a bundled .apkg into Documents (the backend imports from a real
    /// sandbox file path). Returns the destination path.
    private func stageApkg(_ src: URL) throws -> String {
        let fm = FileManager.default
        let docs = try fm.url(for: .documentDirectory, in: .userDomainMask,
                              appropriateFor: nil, create: true)
        let dst = docs.appendingPathComponent(src.lastPathComponent)
        if fm.fileExists(atPath: dst.path) {
            try fm.removeItem(at: dst)
        }
        try fm.copyItem(at: src, to: dst)
        return dst.path
    }

    private func importMarkerURL() throws -> URL {
        let docs = try FileManager.default.url(
            for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
        return docs.appendingPathComponent(".imported_deck")
    }

    private func importedDeckMarker() -> String? {
        guard let u = try? importMarkerURL() else { return nil }
        return try? String(contentsOf: u, encoding: .utf8)
    }

    private func writeImportedDeckMarker(_ name: String) {
        if let u = try? importMarkerURL() {
            try? name.write(to: u, atomically: true, encoding: .utf8)
        }
    }

    /// Delete the sandbox collection + media so a (re)import starts clean.
    private func clearCollection(_ paths: CollectionPaths) throws {
        let fm = FileManager.default
        for p in [paths.collection, paths.collection + "-wal", paths.collection + "-shm",
                  paths.mediaDB] where fm.fileExists(atPath: p) {
            try fm.removeItem(atPath: p)
        }
        if fm.fileExists(atPath: paths.mediaFolder) {
            try fm.removeItem(atPath: paths.mediaFolder)
        }
        try fm.createDirectory(atPath: paths.mediaFolder, withIntermediateDirectories: true)
    }
}
