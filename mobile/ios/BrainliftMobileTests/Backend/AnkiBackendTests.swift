// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import Foundation
import Security
import SwiftProtobuf
import XCTest
@testable import BrainliftMobile

final class AnkiBackendTests: XCTestCase {
    func testOpenCallAndCloseUseOneHandle() async throws {
        let transport = RecordingTransport()
        let backend = AnkiBackend(transport: transport)

        try await backend.open()
        let snapshot = try await backend.brainliftScoreSnapshot(
            topics: [(name: "Biology", tag: "mcat::biology")]
        )
        try await backend.close()

        XCTAssertEqual(snapshot.topics, [])
        XCTAssertEqual(transport.openCount, 1)
        XCTAssertEqual(transport.handlesUsed, [42])
        XCTAssertEqual(transport.closedHandles, [42])
    }

    func testEvidenceSnapshotUsesDesktopMCATTaxonomyInOrder() async throws {
        let expectedNames = [
            "Biochemistry",
            "Biology",
            "General Chemistry",
            "Organic Chemistry",
            "Physics",
            "Psychology and Sociology",
            "Critical Analysis and Reasoning",
        ]
        let expectedTags = [
            "mcat::biochemistry",
            "mcat::biology",
            "mcat::general-chemistry",
            "mcat::organic-chemistry",
            "mcat::physics",
            "mcat::psychology-sociology",
            "mcat::cars",
        ]
        XCTAssertEqual(EvidenceTopic.mcat.map(\.name), expectedNames)
        XCTAssertEqual(EvidenceTopic.mcat.map(\.tag), expectedTags)

        let transport = RecordingTransport()
        let backend = AnkiBackend(transport: transport)
        try await backend.open()
        _ = try await backend.evidenceSnapshot()
        try await backend.close()

        XCTAssertEqual(transport.topicNameRequests, [expectedNames])
        XCTAssertEqual(transport.topicTagRequests, [expectedTags])
    }

    func testCallBeforeOpenFails() async {
        let backend = AnkiBackend(transport: RecordingTransport())

        do {
            _ = try await backend.brainliftScoreSnapshot(topics: [])
            XCTFail("expected notOpen")
        } catch {
            XCTAssertEqual(error as? AnkiBackendError, .notOpen)
        }
    }

    func testCredentialsDescriptionRedactsSecrets() {
        let credentials = SyncCredentials(
            username: "learner@example.com",
            password: "super-secret"
        )

        XCTAssertFalse(credentials.description.contains(credentials.username))
        XCTAssertFalse(credentials.description.contains(credentials.password))
    }

    func testCredentialsRoundTripThroughKeychain() throws {
        let store = SyncCredentialsStore(
            service: "com.techmexdev.BrainliftMobileTests.\(UUID().uuidString)"
        )
        let credentials = SyncCredentials(
            username: "learner@example.com",
            password: "super-secret"
        )
        defer { try? store.delete() }

        try store.save(credentials)
        XCTAssertEqual(try store.load(), credentials)
        try store.delete()
        XCTAssertNil(try store.load())
    }

    func testFailedCredentialReplacementPreservesExistingCredential() throws {
        let existing = SyncCredentials(
            username: "existing@example.com",
            password: "existing-secret"
        )
        let keychain = FailingReplacementKeychain(
            data: try JSONEncoder().encode(existing)
        )
        let store = SyncCredentialsStore(
            service: "com.techmexdev.BrainliftMobileTests",
            keychain: keychain
        )

        XCTAssertThrowsError(
            try store.save(
                SyncCredentials(
                    username: "replacement@example.com",
                    password: "replacement-secret"
                )
            )
        ) { error in
            XCTAssertEqual(error as? KeychainError, KeychainError(errSecAuthFailed))
        }
        XCTAssertEqual(try store.load(), existing)
        XCTAssertEqual(keychain.updateCount, 1)
        XCTAssertEqual(keychain.addCount, 0)
    }

    func testNativeTransportOpensFixtureCollectionAndReadsEvidence() async throws {
        let directory = FileManager.default.temporaryDirectory
            .appending(path: UUID().uuidString, directoryHint: .isDirectory)
        try FileManager.default.createDirectory(
            at: directory,
            withIntermediateDirectories: true
        )
        defer { try? FileManager.default.removeItem(at: directory) }

        let backend = AnkiBackend()
        try await backend.open()
        try await backend.openCollection(in: directory)
        let snapshot = try await backend.brainliftScoreSnapshot(topics: [])
        try await backend.closeCollection()
        try await backend.close()

        XCTAssertEqual(snapshot.topics, [])
        XCTAssertEqual(
            snapshot.readiness.availability,
            .abstained
        )
    }

    func testProgressCanBeReadWhileLongNativeCallIsSuspendedAndCloseIsRejected() async throws {
        let fullSyncStarted = expectation(description: "full sync entered transport")
        let progressReturned = expectation(description: "progress returned during full sync")
        let transport = SuspendedFullSyncTransport(started: fullSyncStarted)
        let backend = AnkiBackend(transport: transport)
        try await backend.open()

        let fullSync = Task {
            try await backend.fullSync(
                auth: Anki_Sync_SyncAuth(),
                direction: .download
            )
        }
        await fulfillment(of: [fullSyncStarted], timeout: 1)

        let progressRead = Task {
            let progress = try await backend.latestSyncProgress()
            progressReturned.fulfill()
            return progress
        }
        await fulfillment(of: [progressReturned], timeout: 1)
        let progress = try await progressRead.value
        XCTAssertEqual(
            progress,
            SyncProgress(
                title: "Transferring collection",
                completed: 3,
                total: 10
            )
        )

        do {
            try await backend.close()
            XCTFail("close must not race an in-flight native call")
        } catch {
            XCTAssertEqual(error as? AnkiBackendError, .busy)
        }

        transport.releaseFullSync()
        try await fullSync.value
        try await backend.close()
        XCTAssertEqual(transport.closeCount, 1)
    }
}

private final class FailingReplacementKeychain:
    KeychainAccess,
    @unchecked Sendable
{
    private let data: Data
    private(set) var updateCount = 0
    private(set) var addCount = 0

    init(data: Data) {
        self.data = data
    }

    func update(
        _ query: [String: Any],
        attributes: [String: Any]
    ) -> OSStatus {
        updateCount += 1
        return errSecAuthFailed
    }

    func add(_ attributes: [String: Any]) -> OSStatus {
        addCount += 1
        return errSecSuccess
    }

    func copyMatching(_ query: [String: Any]) -> KeychainReadResult {
        KeychainReadResult(status: errSecSuccess, data: data)
    }

    func delete(_ query: [String: Any]) -> OSStatus {
        errSecSuccess
    }
}

private final class RecordingTransport: BackendTransport, @unchecked Sendable {
    private let lock = NSLock()
    private(set) var openCount = 0
    private(set) var handlesUsed: [UInt64] = []
    private(set) var closedHandles: [UInt64] = []
    private(set) var topicNameRequests: [[String]] = []
    private(set) var topicTagRequests: [[String]] = []

    func open(request: Data) throws -> UInt64 {
        _ = try Anki_Backend_BackendInit(serializedBytes: request)
        lock.withLock {
            openCount += 1
        }
        return 42
    }

    func run(
        handle: UInt64,
        address: BackendMethodAddress,
        request: Data
    ) throws -> Data {
        XCTAssertEqual(
            address,
            BackendMethods.backendStatsServiceBrainliftScoreSnapshot
        )
        let scoreRequest = try Anki_Stats_BrainliftScoreRequest(
            serializedBytes: request
        )
        lock.withLock {
            handlesUsed.append(handle)
            topicNameRequests.append(scoreRequest.topics.map(\.name))
            topicTagRequests.append(scoreRequest.topics.map(\.tag))
        }
        return try Anki_Stats_BrainliftScoreSnapshotResponse().serializedData()
    }

    func close(handle: UInt64) throws {
        lock.withLock {
            closedHandles.append(handle)
        }
    }
}

private enum SuspendedTransportError: Error {
    case timedOut
    case unexpectedMethod
}

private final class SuspendedFullSyncTransport:
    BackendTransport,
    @unchecked Sendable
{
    private let started: XCTestExpectation
    private let release = DispatchSemaphore(value: 0)
    private let lock = NSLock()
    private(set) var closeCount = 0

    init(started: XCTestExpectation) {
        self.started = started
    }

    func open(request: Data) throws -> UInt64 {
        _ = try Anki_Backend_BackendInit(serializedBytes: request)
        return 42
    }

    func run(
        handle: UInt64,
        address: BackendMethodAddress,
        request: Data
    ) throws -> Data {
        guard handle == 42 else {
            throw SuspendedTransportError.unexpectedMethod
        }
        switch address {
        case BackendMethods.backendSyncServiceFullUploadOrDownload:
            _ = try Anki_Sync_FullUploadOrDownloadRequest(
                serializedBytes: request
            )
            started.fulfill()
            guard release.wait(timeout: .now() + 3) == .success else {
                throw SuspendedTransportError.timedOut
            }
            return try Anki_Generic_Empty().serializedData()
        case BackendMethods.backendCollectionServiceLatestProgress:
            _ = try Anki_Generic_Empty(serializedBytes: request)
            var fullSync = Anki_Collection_Progress.FullSync()
            fullSync.transferred = 3
            fullSync.total = 10
            var progress = Anki_Collection_Progress()
            progress.fullSync = fullSync
            return try progress.serializedData()
        default:
            throw SuspendedTransportError.unexpectedMethod
        }
    }

    func close(handle: UInt64) throws {
        guard handle == 42 else {
            throw SuspendedTransportError.unexpectedMethod
        }
        lock.withLock {
            closeCount += 1
        }
    }

    func releaseFullSync() {
        release.signal()
    }
}
