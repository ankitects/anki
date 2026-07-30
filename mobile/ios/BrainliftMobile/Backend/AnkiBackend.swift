// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import Foundation
import SwiftProtobuf

actor AnkiBackend {
    private let transport: any BackendTransport
    let reviewCollectionDirectory: URL?
    private var handle: UInt64?

    init(
        transport: any BackendTransport = NativeBackendTransport(),
        reviewCollectionDirectory: URL? = nil
    ) {
        self.transport = transport
        self.reviewCollectionDirectory = reviewCollectionDirectory
    }

    func open(preferredLanguages: [String] = ["en"]) throws {
        guard handle == nil else {
            throw AnkiBackendError.alreadyOpen
        }
        var request = Anki_Backend_BackendInit()
        request.preferredLangs = preferredLanguages
        handle = try transport.open(request: try request.serializedData())
    }

    func close() throws {
        guard let handle else {
            throw AnkiBackendError.notOpen
        }
        try transport.close(handle: handle)
        self.handle = nil
    }

    func openCollection(in directory: URL) throws {
        let mediaDirectory = directory.appending(
            path: "collection.media",
            directoryHint: .isDirectory
        )
        try FileManager.default.createDirectory(
            at: mediaDirectory,
            withIntermediateDirectories: true
        )
        var request = Anki_Collection_OpenCollectionRequest()
        request.collectionPath = directory.appending(path: "collection.anki2").path()
        request.mediaFolderPath = mediaDirectory.path()
        request.mediaDbPath = directory.appending(path: "collection.media.db2").path()
        let _: Anki_Generic_Empty = try call(
            BackendMethods.backendCollectionServiceOpenCollection,
            input: request
        )
    }

    func closeCollection() throws {
        let request = Anki_Collection_CloseCollectionRequest()
        let _: Anki_Generic_Empty = try call(
            BackendMethods.backendCollectionServiceCloseCollection,
            input: request
        )
    }

    func call<Input: SwiftProtobuf.Message, Output: SwiftProtobuf.Message>(
        _ address: BackendMethodAddress,
        input: Input
    ) throws -> Output {
        guard let handle else {
            throw AnkiBackendError.notOpen
        }
        let data = try transport.run(
            handle: handle,
            address: address,
            request: try input.serializedData()
        )
        return try Output(serializedBytes: data)
    }

    func brainliftScoreSnapshot(
        topics: [(name: String, tag: String)]
    ) throws -> Anki_Stats_BrainliftScoreSnapshotResponse {
        var request = Anki_Stats_BrainliftScoreRequest()
        request.topics = topics.map { topic in
            var message = Anki_Stats_BrainliftTopic()
            message.name = topic.name
            message.tag = topic.tag
            return message
        }
        return try call(
            BackendMethods.backendStatsServiceBrainliftScoreSnapshot,
            input: request
        )
    }
}
