// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import Foundation

enum SyncPhase: Equatable, Sendable {
    case idle
    case authenticating
    case syncing
    case waitingForDirection([SyncDirection])
    case completed
    case failed
}

@MainActor
final class SyncCoordinator: ObservableObject {
    @Published private(set) var phase: SyncPhase = .idle
    @Published private(set) var message = ""
    @Published private(set) var progress: SyncProgress?

    private let backend: any SyncBackend
    private let credentials: any SyncCredentialProviding
    private var onCompleted: @MainActor @Sendable () async -> Void
    private var pendingAuth: Anki_Sync_SyncAuth?
    private var allowedDirections: Set<SyncDirection> = []

    init(
        backend: any SyncBackend,
        credentials: any SyncCredentialProviding,
        onCompleted: @escaping @MainActor @Sendable () async -> Void = {}
    ) {
        self.backend = backend
        self.credentials = credentials
        self.onCompleted = onCompleted
    }

    var canRetry: Bool {
        phase == .failed
    }

    func setCompletion(
        _ completion: @escaping @MainActor @Sendable () async -> Void
    ) {
        onCompleted = completion
    }

    func sync(isCleanInstall: Bool) async {
        message = ""
        progress = nil
        pendingAuth = nil
        allowedDirections = []
        do {
            guard let credentials = try credentials.load() else {
                throw SyncCoordinatorError.missingCredentials
            }
            phase = .authenticating
            let auth = try await backend.syncLogin(credentials: credentials)
            phase = .syncing
            let required = try await backend.syncCollection(auth: auth)
            switch required {
            case .noChanges, .normalSync:
                await complete()
            case .fullDownload:
                if isCleanInstall {
                    try await backend.fullSync(auth: auth, direction: .download)
                    await complete()
                } else {
                    waitForDirection(auth: auth, allowed: [.download])
                }
            case .fullUpload:
                if isCleanInstall {
                    fail(SyncCoordinatorError.emptyUploadBlocked)
                } else {
                    waitForDirection(auth: auth, allowed: [.upload])
                }
            case .fullSync:
                if isCleanInstall {
                    fail(SyncCoordinatorError.cleanInstallRequiresDownload)
                } else {
                    waitForDirection(auth: auth, allowed: [.download, .upload])
                }
            case .UNRECOGNIZED:
                fail(SyncCoordinatorError.unknownSyncState)
            }
        } catch {
            fail(error)
        }
    }

    func choose(_ direction: SyncDirection) async {
        guard
            let pendingAuth,
            allowedDirections.contains(direction)
        else {
            fail(SyncCoordinatorError.directionNotAllowed)
            return
        }
        phase = .syncing
        do {
            try await backend.fullSync(auth: pendingAuth, direction: direction)
            await complete()
        } catch {
            fail(error)
        }
    }

    func refreshProgress() async {
        do {
            progress = try await backend.latestSyncProgress()
        } catch {
            message = error.localizedDescription
        }
    }

    func report(_ error: Error) {
        fail(error)
    }

    private func waitForDirection(
        auth: Anki_Sync_SyncAuth,
        allowed: Set<SyncDirection>
    ) {
        pendingAuth = auth
        allowedDirections = allowed
        phase = .waitingForDirection(
            SyncDirection.allCases.filter(allowed.contains)
        )
        message = "Choose the collection direction before continuing."
    }

    private func complete() async {
        pendingAuth = nil
        allowedDirections = []
        phase = .completed
        message = "Collection is up to date."
        await onCompleted()
    }

    private func fail(_ error: Error) {
        phase = .failed
        message = error.localizedDescription
    }
}

enum SyncCoordinatorError: LocalizedError {
    case missingCredentials
    case emptyUploadBlocked
    case cleanInstallRequiresDownload
    case directionNotAllowed
    case unknownSyncState

    var errorDescription: String? {
        switch self {
        case .missingCredentials:
            "Enter sync credentials before syncing."
        case .emptyUploadBlocked:
            "An empty clean-install collection can never be uploaded."
        case .cleanInstallRequiresDownload:
            "A clean install requires a backend-declared full download."
        case .directionNotAllowed:
            "That full-sync direction is not allowed."
        case .unknownSyncState:
            "The backend returned an unknown sync state."
        }
    }
}
