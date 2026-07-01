// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
//
// Hand-written C header documenting the ABI exposed by the `anki-ios`
// staticlib (rslib/ios). Not built as part of the Rust crate — this is a
// reference artifact for building a Swift bridging header / module map.
// Semantics are authoritative per
// .factory/runs/2026-07-01-mcat-honest-score-installer-ios/contracts/api.md
// §3; keep this file in sync with rslib/ios/src/lib.rs if either changes.

#ifndef ANKI_IOS_H
#define ANKI_IOS_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// Opaque handle wrapping the Rust `anki::backend::Backend`. Swift never
// inspects its layout; it only ever holds a pointer obtained from
// anki_open() and passes it back to anki_command() / anki_free_backend().
typedef struct AnkiBackend AnkiBackend;

// ---------------------------------------------------------------------
// Buffer-ownership rule (single source of truth; mirrors contracts/api.md §3)
// ---------------------------------------------------------------------
//
// | Buffer                        | Allocated by | Freed by              | Direction               |
// |--------------------------------|--------------|------------------------|--------------------------|
// | init_msg (anki_open)          | Swift        | Swift                  | Swift -> Rust (borrowed) |
// | input (anki_command)          | Swift        | Swift                  | Swift -> Rust (borrowed) |
// | *out_ptr (anki_command)       | Rust         | Swift via anki_free    | Rust -> Swift (transfer) |
// | AnkiBackend* handle           | Rust (anki_open) | Swift via anki_free_backend | Rust -> Swift (transfer) |
//
// Whoever allocates in Rust and hands a pointer across transfers ownership
// to Swift; Swift releases it via the matching anki_free*() function --
// NEVER Swift's native free() (these buffers come from the Rust allocator,
// not libc malloc). Buffers passed IN from Swift (init_msg, input) are
// borrowed for the duration of the call only; Rust never frees or retains
// them.
//
// ---------------------------------------------------------------------
// Threading
// ---------------------------------------------------------------------
//
// The underlying Collection (reachable via the opaque AnkiBackend handle)
// is NOT thread-safe. Swift MUST serialize all anki_command() calls made
// against a given handle (e.g. via a single dispatch queue or actor).
// Concurrent calls on the same handle are undefined behavior, not a
// documented error path. This mirrors the Mutex<Option<Collection>>
// serialization already used internally by BackendInner on the Rust side.

// Initialize a new backend from a serialized anki_proto::backend::BackendInit
// message (the same bytes the Python bridge decodes).
//
// Returns a non-null handle on success. Returns NULL on failure (the reason
// is logged to stderr; there is no payload to inspect).
//
// Ownership: Swift owns the returned handle and must free it exactly once
// via anki_free_backend().
//
// init_msg may be NULL only if init_msg_len == 0.
AnkiBackend *anki_open(const uint8_t *init_msg, size_t init_msg_len);

// Invoke one (service, method) RPC against `backend`, with the same
// semantics as Backend::run_service_method on the Rust side.
//
// `input` is borrowed: Swift-owned, read-only for the duration of the call;
// Rust never frees or retains it. May be NULL only if input_len == 0.
//
// `out_ptr` / `out_len` are OUT parameters. They are always populated when
// the return value is 0 or 1; the returned buffer is heap-allocated by Rust
// and must be released by Swift via exactly one call to anki_free() (never
// Swift's native free()).
//
// Return value:
//   0  = success; *out_ptr / *out_len contain the serialized response
//        protobuf bytes.
//   1  = RPC error; *out_ptr / *out_len contain the serialized
//        anki_proto::backend::BackendError bytes (identical format to the
//        error branch Backend::run_service_method produces internally).
//   -1 = handle/argument error (backend is NULL, or out_ptr/out_len are
//        NULL). *out_ptr / *out_len are left untouched -- do not read them.
//
// Threading: the caller MUST serialize all calls to this function for the
// same `backend` handle; see the Threading note above.
int32_t anki_command(AnkiBackend *backend, uint32_t service, uint32_t method,
                      const uint8_t *input, size_t input_len,
                      uint8_t **out_ptr, size_t *out_len);

// Release a buffer previously written to *out_ptr / *out_len by
// anki_command(). Must be called exactly once per producing call.
// ptr == NULL or len == 0 is a no-op.
void anki_free(uint8_t *ptr, size_t len);

// Release a handle previously returned by anki_open(). Must be called
// exactly once; no anki_command() call may follow it for this handle.
// backend == NULL is a no-op.
void anki_free_backend(AnkiBackend *backend);

#ifdef __cplusplus
}
#endif

#endif // ANKI_IOS_H
