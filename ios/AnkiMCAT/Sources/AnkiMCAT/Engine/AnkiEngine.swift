// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
//
// AnkiEngine — a thin, thread-safe Swift wrapper over the `anki-ios` C ABI
// (anki_open / anki_command / anki_free / anki_free_backend).
//
// The underlying Rust Collection is NOT thread-safe (see contracts/api.md §3
// and anki_ios.h "Threading"): all anki_command calls for a handle MUST be
// serialized. Modeling this as a Swift `actor` gives us that guarantee for
// free — every method hop is a serialized await, so no two anki_command calls
// can ever overlap on the handle. All request/response bodies are typed
// SwiftProtobuf messages (generated from proto/anki/*.proto), so we never
// hand-build wire bytes past the trivial BackendInit case.

import Foundation
import SwiftProtobuf
import AnkiIOS

/// Backend service ids, as dispatched by `Backend::run_service_method` on the
/// Rust side. These are the exact (service, method) pairs the Python bridge
/// (`out/pylib/anki/_backend_generated.py`) passes to the same entry point —
/// verified against that generated code during the C3/C4 build.
enum AnkiService {
    // service 3 = BackendCollectionService
    static let collection: UInt32 = 3
    static let openCollection: UInt32 = 0
    static let checkDatabase: UInt32 = 6

    // service 39 = BackendImportExportService
    static let importExport: UInt32 = 39
    static let importAnkiPackage: UInt32 = 2

    // service 13 = BackendSchedulerService
    static let scheduler: UInt32 = 13
    static let getQueuedCards: UInt32 = 3
    static let answerCard: UInt32 = 4

    // service 27 = BackendCardRenderingService
    static let cardRendering: UInt32 = 27
    static let renderExistingCard: UInt32 = 6

    // service 7 = BackendDecksService
    static let decks: UInt32 = 7
    static let getDeckNames: UInt32 = 13
    static let setCurrentDeck: UInt32 = 22

    // service 11 = BackendDeckConfigService
    static let deckConfig: UInt32 = 11
    static let getDeckConfigsForUpdate: UInt32 = 6
    static let updateDeckConfigs: UInt32 = 7
}

/// Error surfaced across the C ABI seam.
enum AnkiEngineError: Error, CustomStringConvertible {
    /// anki_open returned NULL (BackendInit decode / backend init failed).
    case openFailed
    /// Called before the backend was opened.
    case notOpen
    /// anki_command returned 1: a serialized BackendError from the Rust core.
    case backend(kind: String, message: String)
    /// anki_command returned -1: bad handle/args (should not happen in normal use).
    case argument
    /// A response protobuf failed to decode.
    case decode(String)

    var description: String {
        switch self {
        case .openFailed: return "Failed to open the Anki backend"
        case .notOpen: return "Backend is not open"
        case let .backend(kind, message): return "\(message) [\(kind)]"
        case .argument: return "Invalid argument passed to anki_command"
        case let .decode(what): return "Failed to decode \(what)"
        }
    }
}

/// Serializes every call into the Rust backend on a single actor executor,
/// satisfying the "one queue/actor per handle" threading contract.
actor AnkiEngine {
    private var handle: OpaquePointer?

    // MARK: - Lifecycle

    /// Open a backend from a BackendInit message (same bytes Python decodes).
    func open(preferredLangs: [String] = ["en"]) throws {
        if handle != nil { return }
        var initMsg = Anki_Backend_BackendInit()
        initMsg.preferredLangs = preferredLangs
        initMsg.server = false
        let bytes = try initMsg.serializedData()
        let h: OpaquePointer? = bytes.withUnsafeBytes { raw in
            anki_open(raw.bindMemory(to: UInt8.self).baseAddress, raw.count)
        }
        guard let opened = h else { throw AnkiEngineError.openFailed }
        handle = opened
    }

    /// Release the backend handle. Safe to call more than once.
    func close() {
        if let h = handle {
            anki_free_backend(h)
            handle = nil
        }
    }

    deinit {
        if let h = handle { anki_free_backend(h) }
    }

    // MARK: - Raw command dispatch (serialized by the actor)

    /// Invoke one (service, method) RPC. Copies the Rust-owned response buffer
    /// into a Swift `Data`, then releases it via `anki_free` (never Swift's
    /// native free — the buffer is from the Rust allocator).
    private func command(service: UInt32, method: UInt32, input: Data) throws -> Data {
        guard let backend = handle else { throw AnkiEngineError.notOpen }

        var outPtr: UnsafeMutablePointer<UInt8>? = nil
        var outLen: Int = 0

        let rc: Int32 = input.withUnsafeBytes { raw in
            anki_command(backend, service, method,
                         raw.bindMemory(to: UInt8.self).baseAddress, raw.count,
                         &outPtr, &outLen)
        }

        // Copy out the Rust-owned buffer (if any) then free it exactly once.
        var out = Data()
        if rc == 0 || rc == 1 {
            if let p = outPtr, outLen > 0 {
                out = Data(bytes: p, count: outLen)
            }
            anki_free(outPtr, outLen)
        }

        switch rc {
        case 0:
            return out
        case 1:
            // Serialized anki_proto::backend::BackendError.
            if let err = try? Anki_Backend_BackendError(serializedBytes: out) {
                throw AnkiEngineError.backend(kind: "\(err.kind)", message: err.message)
            }
            throw AnkiEngineError.backend(kind: "UNKNOWN", message: "backend error")
        default:
            throw AnkiEngineError.argument
        }
    }

    /// Typed helper: encode `request`, dispatch, decode into `Response`.
    private func call<Request: SwiftProtobuf.Message, Response: SwiftProtobuf.Message>(
        service: UInt32, method: UInt32, _ request: Request, returning: Response.Type
    ) throws -> Response {
        let input = try request.serializedData()
        let output = try command(service: service, method: method, input: input)
        do {
            return try Response(serializedBytes: output)
        } catch {
            throw AnkiEngineError.decode(String(describing: Response.self))
        }
    }

    // MARK: - Collection

    /// Open (or create) a collection at the given sandbox paths.
    func openCollection(collectionPath: String, mediaFolderPath: String, mediaDbPath: String) throws {
        var req = Anki_Collection_OpenCollectionRequest()
        req.collectionPath = collectionPath
        req.mediaFolderPath = mediaFolderPath
        req.mediaDbPath = mediaDbPath
        _ = try call(service: AnkiService.collection, method: AnkiService.openCollection,
                     req, returning: Anki_Generic_Empty.self)
    }

    // MARK: - Import

    /// Import an .apkg from the sandbox. Returns the number of notes found in
    /// the package (ImportResponse.log.foundNotes).
    @discardableResult
    func importAnkiPackage(packagePath: String) throws -> UInt32 {
        var req = Anki_ImportExport_ImportAnkiPackageRequest()
        req.packagePath = packagePath
        // Defaults are fine for a fresh import: keep scheduling + deck configs.
        var opts = Anki_ImportExport_ImportAnkiPackageOptions()
        opts.withScheduling = true
        opts.withDeckConfigs = true
        req.options = opts
        let resp = try call(service: AnkiService.importExport, method: AnkiService.importAnkiPackage,
                            req, returning: Anki_ImportExport_ImportResponse.self)
        return resp.log.foundNotes
    }

    // MARK: - Decks

    /// All deck (id, name) pairs, excluding the empty Default deck.
    func deckNames() throws -> [Anki_Decks_DeckNameId] {
        var req = Anki_Decks_GetDeckNamesRequest()
        req.skipEmptyDefault = true
        req.includeFiltered = false
        let resp = try call(service: AnkiService.decks, method: AnkiService.getDeckNames,
                            req, returning: Anki_Decks_DeckNames.self)
        return resp.entries
    }

    /// Select the deck to study. The scheduler builds its queue from the
    /// current deck's subtree, so this must be set to a deck that has cards.
    func setCurrentDeck(_ deckID: Int64) throws {
        var req = Anki_Decks_DeckId()
        req.did = deckID
        _ = try call(service: AnkiService.decks, method: AnkiService.setCurrentDeck,
                     req, returning: Anki_Collection_OpChanges.self)
    }

    /// Make new cards gather across every category (RANDOM_CARDS) rather than
    /// draining one subdeck at a time (the default DECK order). This mirrors the
    /// desktop "Start Flashcards" behaviour so the iOS daily new cards are spread
    /// across topics. Idempotent, and respects the daily new-card limit — it only
    /// changes the *order* cards are introduced, not how many. The apkg config
    /// round-trip does not reliably carry this field, so we set it at runtime.
    func ensureRandomCardGather(deckID: Int64) throws {
        var getReq = Anki_Decks_DeckId()
        getReq.did = deckID
        let data = try call(service: AnkiService.deckConfig,
                            method: AnkiService.getDeckConfigsForUpdate,
                            getReq, returning: Anki_DeckConfig_DeckConfigsForUpdate.self)

        var configs: [Anki_DeckConfig_DeckConfig] = []
        var changed = false
        for entry in data.allConfig {
            var cfg = entry.config
            if cfg.config.newCardGatherPriority != .randomCards {
                cfg.config.newCardGatherPriority = .randomCards
                changed = true
            }
            configs.append(cfg)
        }
        guard changed else { return }

        var upd = Anki_DeckConfig_UpdateDeckConfigsRequest()
        upd.targetDeckID = deckID
        upd.configs = configs
        upd.removedConfigIds = []
        upd.mode = .normal
        upd.cardStateCustomizer = data.cardStateCustomizer
        upd.limits = data.currentDeck.limits
        upd.newCardsIgnoreReviewLimit = data.newCardsIgnoreReviewLimit
        upd.fsrs = data.fsrs
        upd.applyAllParentLimits = data.applyAllParentLimits
        upd.fsrsReschedule = false
        upd.fsrsHealthCheck = false
        _ = try call(service: AnkiService.deckConfig,
                     method: AnkiService.updateDeckConfigs,
                     upd, returning: Anki_Collection_OpChanges.self)
    }

    // MARK: - Scheduler / review loop

    /// Fetch the current queue head. `cards` is empty when the queue is done.
    func queuedCards(fetchLimit: UInt32 = 1) throws -> Anki_Scheduler_QueuedCards {
        var req = Anki_Scheduler_GetQueuedCardsRequest()
        req.fetchLimit = fetchLimit
        req.intradayLearningOnly = false
        return try call(service: AnkiService.scheduler, method: AnkiService.getQueuedCards,
                        req, returning: Anki_Scheduler_QueuedCards.self)
    }

    /// Grade a card. Builds a CardAnswer from the queued card's scheduling
    /// states + the chosen rating (exactly as the desktop/web reviewer does),
    /// then advances the scheduler. Returns the resulting OpChanges.
    @discardableResult
    func answer(card: Anki_Scheduler_QueuedCards.QueuedCard,
                rating: Anki_Scheduler_CardAnswer.Rating) throws -> Anki_Collection_OpChanges {
        var ans = Anki_Scheduler_CardAnswer()
        ans.cardID = card.card.id
        ans.currentState = card.states.current
        ans.newState = newState(for: rating, from: card.states)
        ans.rating = rating
        ans.answeredAtMillis = Int64(Date().timeIntervalSince1970 * 1000)
        ans.millisecondsTaken = 1000
        return try call(service: AnkiService.scheduler, method: AnkiService.answerCard,
                        ans, returning: Anki_Collection_OpChanges.self)
    }

    private func newState(for rating: Anki_Scheduler_CardAnswer.Rating,
                          from states: Anki_Scheduler_SchedulingStates) -> Anki_Scheduler_SchedulingState {
        switch rating {
        case .again: return states.again
        case .hard: return states.hard
        case .good: return states.good
        case .easy: return states.easy
        default: return states.good
        }
    }

    // MARK: - Rendering

    /// Render a card's question + answer HTML by concatenating the rendered
    /// template nodes (literal text + resolved field replacements).
    func render(cardID: Int64) throws -> (question: String, answer: String, css: String) {
        var req = Anki_CardRendering_RenderExistingCardRequest()
        req.cardID = cardID
        req.browser = false
        req.partialRender = false
        let resp = try call(service: AnkiService.cardRendering, method: AnkiService.renderExistingCard,
                            req, returning: Anki_CardRendering_RenderCardResponse.self)
        return (assemble(resp.questionNodes), assemble(resp.answerNodes), resp.css)
    }

    private func assemble(_ nodes: [Anki_CardRendering_RenderedTemplateNode]) -> String {
        var html = ""
        for node in nodes {
            switch node.value {
            case let .text(t): html += t
            case let .replacement(r): html += r.currentText
            case .none: break
            }
        }
        return html
    }
}
