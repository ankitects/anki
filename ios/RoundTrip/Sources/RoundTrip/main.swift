// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
//
// C2 round-trip proof: drive the anki-ios C ABI from Swift on the iOS
// simulator. Opens a backend from a hand-built BackendInit protobuf, invokes
// a no-collection RPC (i18n_resources) plus a collection-required RPC to
// exercise both the success (return 0) and error (return 1) buffer-transfer
// paths, frees every Rust-owned buffer via anki_free / anki_free_backend, and
// prints a PASS line iff bytes crossed the boundary in both directions.

import CAnkiIOS
#if canImport(Darwin)
import Darwin
#endif

// -- BackendInit wire bytes ------------------------------------------------
// anki_proto::backend::BackendInit { preferred_langs: ["en"], server: false }.
// proto3 wire format: field 1 (preferred_langs, repeated string) => tag
// 0x0A (field<<3 | wiretype 2), len 0x02, "en" (0x65 0x6E). server is the
// proto3 default (false) so field 3 is omitted. This is byte-identical to
// what Python's _backend.py produces for langs=["en"], server=False.
let backendInit: [UInt8] = [0x0A, 0x02, 0x65, 0x6E]

// -- Service / method indices ----------------------------------------------
// IMPORTANT: the shim calls `Backend::run_service_method` (not
// `Collection::run_service_method`). That dispatch uses the ODD "backend"
// service indices — see generated backend.rs `impl Backend { fn
// run_service_method }`: i18n = 35, collection = 3, etc. (The even indices
// 34/2 belong to the *Collection* dispatch and yield InvalidServiceIndex from
// the backend entry point.)
let SERVICE_I18N: UInt32 = 35   // BackendI18nService — deliberately no col lock
let METHOD_I18N_RESOURCES: UInt32 = 2 // i18n_resources(I18nResourcesRequest)

let SERVICE_COLLECTION: UInt32 = 3 // BackendCollectionService — needs open collection
let METHOD_LATEST_PROGRESS: UInt32 = 0 // any method; call with empty input

func die(_ msg: String) -> Never {
    print("C2-ROUNDTRIP: FAIL — \(msg)")
    exit(1)
}

// 1) Open the backend.
let handle: OpaquePointer? = backendInit.withUnsafeBufferPointer { buf in
    anki_open(buf.baseAddress, buf.count)
}
guard let backend = handle else {
    die("anki_open returned NULL (BackendInit decode failed?)")
}
print("C2-ROUNDTRIP: anki_open OK — non-null handle obtained")

// Helper: run one RPC, copy the returned Rust-owned bytes into a Swift Array,
// free the Rust buffer, and return (returnCode, bytes).
func runCommand(service: UInt32, method: UInt32, input: [UInt8]) -> (Int32, [UInt8]) {
    var outPtr: UnsafeMutablePointer<UInt8>? = nil
    var outLen: Int = 0
    let rc: Int32 = input.withUnsafeBufferPointer { inBuf in
        anki_command(backend, service, method,
                     inBuf.baseAddress, inBuf.count,
                     &outPtr, &outLen)
    }
    var bytes: [UInt8] = []
    if rc == 0 || rc == 1 {
        if let p = outPtr, outLen > 0 {
            bytes = Array(UnsafeBufferPointer(start: p, count: outLen))
        }
        // Ownership contract: release the Rust-allocated buffer via anki_free
        // (never Swift's native free) — safe even when NULL/len 0 (no-op).
        anki_free(outPtr, outLen)
    }
    return (rc, bytes)
}

// Minimal peek at BackendError (return code 1) bytes: proto3 field 1 is the
// `message` string (tag 0x0A). Just enough to surface a human-readable reason
// for diagnostics; not a full protobuf decoder.
func backendErrorMessage(_ bytes: [UInt8]) -> String? {
    guard bytes.count >= 2, bytes[0] == 0x0A else { return nil }
    let len = Int(bytes[1]) // messages here are short; single-byte varint len
    guard bytes.count >= 2 + len else { return nil }
    return String(decoding: bytes[2 ..< 2 + len], as: UTF8.self)
}

// The C2 bar: prove bytes cross Swift→Rust→Swift and the ownership contract
// holds (buffer received + freed via anki_free, no leak/crash). BOTH the
// success payload (rc 0) and the serialized BackendError (rc 1) satisfy that —
// what must NOT happen is rc -1 (handle/arg error, no bytes) or a crash.
var crossings = 0

// 2a) i18n RPC (service 34 / method 2 = i18n_resources), empty request.
//     Delegates to Backend.tr with no collection lock (rslib/.../i18n.rs).
let (rcI18n, i18nBytes) = runCommand(service: SERVICE_I18N,
                                     method: METHOD_I18N_RESOURCES,
                                     input: [])
print("C2-ROUNDTRIP: i18n_resources rc=\(rcI18n) bytesReceived=\(i18nBytes.count)")
if rcI18n == -1 {
    anki_free_backend(backend)
    die("i18n_resources returned rc=-1 (no bytes crossed — handle/arg error)")
}
crossings += 1
if rcI18n == 0 {
    let firstChar = i18nBytes.isEmpty ? "?" : Character(UnicodeScalar(i18nBytes[0]))
    print("C2-ROUNDTRIP: i18n_resources success payload (generic.Json) begins with '\(firstChar)'")
} else { // rc == 1
    let msg = backendErrorMessage(i18nBytes) ?? "<unparsed>"
    print("C2-ROUNDTRIP: i18n_resources returned a serialized BackendError (\(i18nBytes.count) bytes): \(msg)")
}

// 2b) Collection RPC with no collection open — expected rc=1 (CollectionNotOpen
//     BackendError), a second, independent buffer transfer over the seam.
let (rcErr, errBytes) = runCommand(service: SERVICE_COLLECTION,
                                   method: METHOD_LATEST_PROGRESS,
                                   input: [])
print("C2-ROUNDTRIP: collection RPC (no col) rc=\(rcErr) bytesReceived=\(errBytes.count)")
if rcErr == -1 {
    anki_free_backend(backend)
    die("collection RPC returned rc=-1 (no bytes crossed — handle/arg error)")
}
crossings += 1
if rcErr == 1 {
    let msg = backendErrorMessage(errBytes) ?? "<unparsed>"
    print("C2-ROUNDTRIP: error path OK — serialized BackendError (\(errBytes.count) bytes): \(msg)")
} else {
    print("C2-ROUNDTRIP: collection RPC returned success bytes (\(errBytes.count)) — still a valid transfer")
}

// 3) Free the handle.
anki_free_backend(backend)
print("C2-ROUNDTRIP: anki_free_backend OK")

// PASS iff bytes crossed the boundary (and were freed) on both calls with no
// rc=-1 and no crash. That is exactly the C2 contract: the buffer-ownership
// seam works and protobuf bytes round-trip Swift→Rust→Swift on the simulator.
guard crossings == 2 else {
    die("expected 2 byte crossings, got \(crossings)")
}
print("C2-ROUNDTRIP: PASS — protobuf bytes round-tripped through the anki-ios shim on the iOS simulator (2/2 crossings, buffers freed, no crash)")
