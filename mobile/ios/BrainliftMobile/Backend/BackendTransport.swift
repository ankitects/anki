// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import Foundation
import SwiftProtobuf

protocol BackendTransport: Sendable {
    func open(request: Data) throws -> UInt64
    func run(
        handle: UInt64,
        address: BackendMethodAddress,
        request: Data
    ) throws -> Data
    func close(handle: UInt64) throws
}

struct NativeBackendTransport: BackendTransport {
    func open(request: Data) throws -> UInt64 {
        let result = request.withUnsafeBytes { rawBuffer in
            anki_backend_open(
                AnkiByteSlice(
                    data: rawBuffer.bindMemory(to: UInt8.self).baseAddress,
                    len: rawBuffer.count
                )
            )
        }
        if result.error.token != 0 {
            throw decodeErrorAndFree(result.error)
        }
        guard result.handle != 0 else {
            throw AnkiBackendError.invalidNativeBuffer
        }
        return result.handle
    }

    func run(
        handle: UInt64,
        address: BackendMethodAddress,
        request: Data
    ) throws -> Data {
        let result = request.withUnsafeBytes { rawBuffer in
            anki_backend_run_method(
                handle,
                address.service,
                address.method,
                AnkiByteSlice(
                    data: rawBuffer.bindMemory(to: UInt8.self).baseAddress,
                    len: rawBuffer.count
                )
            )
        }
        if result.error.token != 0 {
            throw decodeErrorAndFree(result.error)
        }
        return try copyAndFree(result.output)
    }

    func close(handle: UInt64) throws {
        let error = anki_backend_close(handle)
        if error.token != 0 {
            throw decodeErrorAndFree(error)
        }
    }

    private func copyAndFree(_ buffer: AnkiOwnedBuffer) throws -> Data {
        defer { anki_backend_buffer_free(buffer) }
        guard buffer.len == 0 || buffer.data != nil else {
            throw AnkiBackendError.invalidNativeBuffer
        }
        guard let data = buffer.data else {
            return Data()
        }
        return Data(bytes: data, count: buffer.len)
    }

    private func decodeErrorAndFree(_ buffer: AnkiOwnedBuffer) -> AnkiBackendError {
        let data = (try? copyAndFree(buffer)) ?? Data()
        guard let backendError = try? Anki_Backend_BackendError(serializedBytes: data) else {
            return .invalidNativeBuffer
        }
        return .native(kind: backendError.kind.rawValue, message: backendError.message)
    }
}
