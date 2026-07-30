// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import XCTest
@testable import BrainliftMobile

@MainActor
final class SyncCoordinatorTests: XCTestCase {
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
        XCTAssertEqual(coordinator.phase, .completed)
        XCTAssertEqual(completion.calls, 1)
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
        let backend = SyncBackendSpy(required: .fullSync)
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
        XCTAssertEqual(coordinator.phase, .completed)
        XCTAssertEqual(completion.calls, 1)
    }

    func testNetworkFailurePreservesRetryPathAndDoesNotRefreshScores() async {
        let backend = SyncBackendSpy(
            required: .normalSync,
            error: SyncFailure.network
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
        XCTAssertEqual(backend.collectionCloseCalls, 0)
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
    let error: Error?
    var progress: SyncProgress?
    private(set) var fullSyncDirections: [SyncDirection] = []
    private(set) var collectionCloseCalls = 0

    init(
        required: Anki_Sync_SyncCollectionResponse.ChangesRequired,
        error: Error? = nil
    ) {
        self.required = required
        self.error = error
    }

    func syncLogin(
        credentials: SyncCredentials
    ) async throws -> Anki_Sync_SyncAuth {
        if let error { throw error }
        var auth = Anki_Sync_SyncAuth()
        auth.hkey = "test-key"
        return auth
    }

    func syncCollection(
        auth: Anki_Sync_SyncAuth
    ) async throws -> Anki_Sync_SyncCollectionResponse.ChangesRequired {
        if let error { throw error }
        return required
    }

    func fullSync(
        auth: Anki_Sync_SyncAuth,
        direction: SyncDirection
    ) async throws {
        if let error { throw error }
        fullSyncDirections.append(direction)
    }

    func latestSyncProgress() async throws -> SyncProgress? {
        progress
    }
}
