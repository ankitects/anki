// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import XCTest
@testable import BrainliftMobile

@MainActor
final class SyncCoordinatorTests: XCTestCase {
    func testContinuationCopiesRedirectOntoExistingAuth() {
        var auth = Anki_Sync_SyncAuth()
        auth.hkey = "test-key"
        auth.endpoint = "https://original.example.com/"
        auth.ioTimeoutSecs = 30
        var response = Anki_Sync_SyncCollectionResponse()
        response.required = .fullDownload
        response.newEndpoint = "https://redirect.example.com/"

        let continuation = SyncContinuation(response: response, auth: auth)

        XCTAssertEqual(continuation.required, .fullDownload)
        XCTAssertEqual(continuation.auth.hkey, "test-key")
        XCTAssertEqual(
            continuation.auth.endpoint,
            "https://redirect.example.com/"
        )
        XCTAssertEqual(continuation.auth.ioTimeoutSecs, 30)
    }

    func testCleanInstallAutomaticallyAcceptsOnlyFullDownload() async {
        let backend = SyncBackendSpy(required: .fullDownload)
        let completion = CompletionSpy()
        let coordinator = SyncCoordinator(
            backend: backend,
            credentials: CredentialsStub(),
            onCompleted: { completion.calls += 1 }
        )

        await coordinator.sync(isCleanInstall: true)

        XCTAssertEqual(backend.fullSyncDirections, [.download])
        XCTAssertEqual(backend.fullSyncAuthEndpoints, [nil])
        XCTAssertEqual(coordinator.phase, .completed)
        XCTAssertEqual(completion.calls, 1)
    }

    func testCleanInstallUsesRedirectEndpointForAutomaticDownload() async {
        let backend = SyncBackendSpy(
            required: .fullDownload,
            redirectedEndpoint: "https://redirect.example.com/"
        )
        let coordinator = SyncCoordinator(
            backend: backend,
            credentials: CredentialsStub()
        )

        await coordinator.sync(isCleanInstall: true)

        XCTAssertEqual(backend.fullSyncDirections, [.download])
        XCTAssertEqual(
            backend.fullSyncAuthEndpoints,
            ["https://redirect.example.com/"]
        )
        XCTAssertEqual(coordinator.phase, .completed)
    }

    func testCleanInstallBlocksBackendDeclaredFullUpload() async {
        let backend = SyncBackendSpy(required: .fullUpload)
        let coordinator = SyncCoordinator(
            backend: backend,
            credentials: CredentialsStub()
        )

        await coordinator.sync(isCleanInstall: true)

        XCTAssertEqual(backend.fullSyncDirections, [])
        XCTAssertEqual(coordinator.phase, .failed)
        XCTAssertTrue(coordinator.message.contains("empty"))
    }

    func testLaterFullSyncWaitsForExplicitDirection() async {
        let backend = SyncBackendSpy(
            required: .fullSync,
            redirectedEndpoint: "https://redirect.example.com/"
        )
        let completion = CompletionSpy()
        let coordinator = SyncCoordinator(
            backend: backend,
            credentials: CredentialsStub(),
            onCompleted: { completion.calls += 1 }
        )

        await coordinator.sync(isCleanInstall: false)
        XCTAssertEqual(
            coordinator.phase,
            .waitingForDirection([.download, .upload])
        )
        XCTAssertEqual(completion.calls, 0)

        await coordinator.choose(.upload)

        XCTAssertEqual(backend.fullSyncDirections, [.upload])
        XCTAssertEqual(
            backend.fullSyncAuthEndpoints,
            ["https://redirect.example.com/"]
        )
        XCTAssertEqual(coordinator.phase, .completed)
        XCTAssertEqual(completion.calls, 1)
    }

    func testDirectionDoubleTapDispatchesOnlyOneFullSync() async {
        let gate = FullSyncGate()
        let backend = SyncBackendSpy(required: .fullSync, fullSyncGate: gate)
        let completion = CompletionSpy()
        let coordinator = SyncCoordinator(
            backend: backend,
            credentials: CredentialsStub(),
            onCompleted: { completion.calls += 1 }
        )

        await coordinator.sync(isCleanInstall: false)
        let firstChoice = Task { await coordinator.choose(.download) }
        await gate.waitUntilSuspended()

        await coordinator.choose(.download)

        XCTAssertEqual(backend.fullSyncDirections, [.download])
        XCTAssertEqual(coordinator.phase, .syncing)
        XCTAssertEqual(completion.calls, 0)

        await gate.release()
        await firstChoice.value

        XCTAssertEqual(backend.fullSyncDirections, [.download])
        XCTAssertEqual(coordinator.phase, .completed)
        XCTAssertEqual(completion.calls, 1)
    }

    func testNetworkFailurePreservesRetryPathAndDoesNotRefreshScores() async {
        let backend = SyncBackendSpy(
            required: .normalSync,
            syncCollectionFailures: [SyncFailure.network]
        )
        backend.progress = SyncProgress(
            title: "Collection remains open",
            completed: nil,
            total: nil
        )
        let completion = CompletionSpy()
        let coordinator = SyncCoordinator(
            backend: backend,
            credentials: CredentialsStub(),
            onCompleted: { completion.calls += 1 }
        )

        await coordinator.sync(isCleanInstall: false)

        XCTAssertEqual(coordinator.phase, .failed)
        XCTAssertTrue(coordinator.canRetry)
        XCTAssertEqual(completion.calls, 0)

        await coordinator.refreshProgress()

        XCTAssertEqual(coordinator.progress, backend.progress)
        XCTAssertEqual(backend.latestProgressCalls, 1)
        XCTAssertEqual(completion.calls, 0)

        await coordinator.sync(isCleanInstall: false)

        XCTAssertEqual(coordinator.phase, .completed)
        XCTAssertEqual(backend.syncCollectionCalls, 2)
        XCTAssertEqual(completion.calls, 1)
    }

    func testProgressComesFromRustBackend() async {
        let backend = SyncBackendSpy(required: .normalSync)
        backend.progress = SyncProgress(
            title: "Downloading collection",
            completed: 2,
            total: 10
        )
        let coordinator = SyncCoordinator(
            backend: backend,
            credentials: CredentialsStub()
        )

        await coordinator.refreshProgress()

        XCTAssertEqual(coordinator.progress, backend.progress)
    }
}

private enum SyncFailure: Error {
    case network
}

private final class CompletionSpy: @unchecked Sendable {
    var calls = 0
}

private struct CredentialsStub: SyncCredentialProviding {
    func load() throws -> SyncCredentials? {
        SyncCredentials(username: "learner@example.com", password: "secret")
    }
}

private final class SyncBackendSpy: SyncBackend, @unchecked Sendable {
    let required: Anki_Sync_SyncCollectionResponse.ChangesRequired
    let redirectedEndpoint: String?
    var syncCollectionFailures: [Error]
    var progress: SyncProgress?
    private(set) var fullSyncDirections: [SyncDirection] = []
    private(set) var fullSyncAuthEndpoints: [String?] = []
    private(set) var syncCollectionCalls = 0
    private(set) var latestProgressCalls = 0
    private let fullSyncGate: FullSyncGate?

    init(
        required: Anki_Sync_SyncCollectionResponse.ChangesRequired,
        redirectedEndpoint: String? = nil,
        syncCollectionFailures: [Error] = [],
        fullSyncGate: FullSyncGate? = nil
    ) {
        self.required = required
        self.redirectedEndpoint = redirectedEndpoint
        self.syncCollectionFailures = syncCollectionFailures
        self.fullSyncGate = fullSyncGate
    }

    func syncLogin(
        credentials: SyncCredentials
    ) async throws -> Anki_Sync_SyncAuth {
        var auth = Anki_Sync_SyncAuth()
        auth.hkey = "test-key"
        return auth
    }

    func syncCollection(
        auth: Anki_Sync_SyncAuth
    ) async throws -> SyncContinuation {
        syncCollectionCalls += 1
        if !syncCollectionFailures.isEmpty {
            throw syncCollectionFailures.removeFirst()
        }
        var continuedAuth = auth
        if let redirectedEndpoint {
            continuedAuth.endpoint = redirectedEndpoint
        }
        return SyncContinuation(required: required, auth: continuedAuth)
    }

    func fullSync(
        auth: Anki_Sync_SyncAuth,
        direction: SyncDirection
    ) async throws {
        fullSyncDirections.append(direction)
        fullSyncAuthEndpoints.append(auth.hasEndpoint ? auth.endpoint : nil)
        if let fullSyncGate {
            await fullSyncGate.suspend()
        }
    }

    func latestSyncProgress() async throws -> SyncProgress? {
        latestProgressCalls += 1
        return progress
    }
}

private actor FullSyncGate {
    private var isSuspended = false
    private var suspensionWaiters: [CheckedContinuation<Void, Never>] = []
    private var releaseContinuation: CheckedContinuation<Void, Never>?

    func suspend() async {
        isSuspended = true
        suspensionWaiters.forEach { $0.resume() }
        suspensionWaiters = []
        await withCheckedContinuation { continuation in
            releaseContinuation = continuation
        }
    }

    func waitUntilSuspended() async {
        if isSuspended {
            return
        }
        await withCheckedContinuation { continuation in
            suspensionWaiters.append(continuation)
        }
    }

    func release() {
        isSuspended = false
        releaseContinuation?.resume()
        releaseContinuation = nil
    }
}
