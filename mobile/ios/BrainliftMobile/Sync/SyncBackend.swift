// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import Foundation

enum SyncDirection: String, CaseIterable, Equatable, Hashable, Identifiable, Sendable {
    case download
    case upload

    var id: Self { self }
}

struct SyncProgress: Equatable, Sendable {
    let title: String
    let completed: UInt32?
    let total: UInt32?
}

protocol SyncBackend: Sendable {
    func syncLogin(
        credentials: SyncCredentials
    ) async throws -> Anki_Sync_SyncAuth
    func syncCollection(
        auth: Anki_Sync_SyncAuth
    ) async throws -> Anki_Sync_SyncCollectionResponse.ChangesRequired
    func fullSync(
        auth: Anki_Sync_SyncAuth,
        direction: SyncDirection
    ) async throws
    func latestSyncProgress() async throws -> SyncProgress?
}

extension AnkiBackend: SyncBackend {
    func syncLogin(
        credentials: SyncCredentials
    ) async throws -> Anki_Sync_SyncAuth {
        var request = Anki_Sync_SyncLoginRequest()
        request.username = credentials.username
        request.password = credentials.password
        return try call(
            BackendMethods.backendSyncServiceSyncLogin,
            input: request
        )
    }

    func syncCollection(
        auth: Anki_Sync_SyncAuth
    ) async throws -> Anki_Sync_SyncCollectionResponse.ChangesRequired {
        var request = Anki_Sync_SyncCollectionRequest()
        request.auth = auth
        request.syncMedia = false
        let response: Anki_Sync_SyncCollectionResponse = try call(
            BackendMethods.backendSyncServiceSyncCollection,
            input: request
        )
        return response.required
    }

    func fullSync(
        auth: Anki_Sync_SyncAuth,
        direction: SyncDirection
    ) async throws {
        var request = Anki_Sync_FullUploadOrDownloadRequest()
        request.auth = auth
        request.upload = direction == .upload
        let _: Anki_Generic_Empty = try call(
            BackendMethods.backendSyncServiceFullUploadOrDownload,
            input: request
        )
    }

    func latestSyncProgress() async throws -> SyncProgress? {
        let response: Anki_Collection_Progress = try call(
            BackendMethods.backendCollectionServiceLatestProgress,
            input: Anki_Generic_Empty()
        )
        switch response.value {
        case .some(.fullSync(let progress)):
            return SyncProgress(
                title: "Transferring collection",
                completed: progress.transferred,
                total: progress.total
            )
        case .some(.normalSync(let progress)):
            return SyncProgress(
                title: progress.stage.isEmpty ? "Syncing collection" : progress.stage,
                completed: nil,
                total: nil
            )
        case .some(.mediaSync(let progress)):
            return SyncProgress(
                title: progress.checked.isEmpty ? "Syncing media" : progress.checked,
                completed: nil,
                total: nil
            )
        case .some(.mediaCheck(let value)):
            return SyncProgress(title: value, completed: nil, total: nil)
        case .some(.none), .some(.databaseCheck), .some(.importing),
             .some(.exporting), .some(.computeParams), .some(.computeRetention),
             .some(.computeMemory), Optional.none:
            return nil
        }
    }
}

protocol SyncCredentialProviding: Sendable {
    func load() throws -> SyncCredentials?
}

extension SyncCredentialsStore: SyncCredentialProviding {}
