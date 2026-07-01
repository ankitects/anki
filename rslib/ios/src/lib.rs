// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! C ABI staticlib bridge between `anki`'s Rust backend and the Swift `ios`
//! worker. Modeled on `pylib/rsbridge/lib.rs` (same `init_backend` +
//! `Backend::run_service_method` wrap), but exposed as a plain C ABI instead
//! of PyO3 bindings, so it can be linked directly into an iOS app via a
//! bridging header (see `anki_ios.h` in this crate).
//!
//! ## Buffer ownership (see contracts/api.md §3 for the authoritative table)
//!
//! | Buffer                        | Allocated by | Freed by             | Direction              |
//! |--------------------------------|--------------|-----------------------|-------------------------|
//! | `init_msg` (`anki_open`)       | Swift        | Swift                 | Swift→Rust (borrowed)   |
//! | `input` (`anki_command`)       | Swift        | Swift                 | Swift→Rust (borrowed)   |
//! | `*out_ptr` (`anki_command`)    | Rust         | Swift via `anki_free` | Rust→Swift (transfer)   |
//! | `AnkiBackend*` handle           | Rust (`anki_open`) | Swift via `anki_free_backend` | Rust→Swift (transfer) |
//!
//! Whoever allocates in Rust and hands a pointer across transfers ownership
//! to Swift; Swift releases it via the matching `anki_free*` function (never
//! Swift's native `free()` — these buffers come from the Rust allocator).
//! Buffers passed **in** from Swift (`init_msg`, `input`) are borrowed for
//! the duration of the call only; Rust never frees or retains them.
//!
//! ## Threading
//!
//! `Collection` (reachable via the opaque `AnkiBackend` handle) is NOT
//! thread-safe. Swift MUST serialize all `anki_command` calls made against a
//! given handle (e.g. via a single dispatch queue or actor). Concurrent
//! calls on the same handle are undefined behavior, not a documented error
//! path. This mirrors the `Mutex<Option<Collection>>` serialization already
//! used internally by `BackendInner`.

use anki::backend::init_backend;
use anki::backend::Backend;

/// Opaque handle wrapping the Rust `Backend`. Swift never inspects its
/// layout; it only ever holds a `*mut AnkiBackend` obtained from
/// [`anki_open`] and passes it back to [`anki_command`] /
/// [`anki_free_backend`].
#[repr(C)]
pub struct AnkiBackend(Backend);

/// Build a `&[u8]` from a raw pointer + length, treating a null pointer (or
/// zero length) as an empty slice rather than invoking UB via
/// `slice::from_raw_parts` on a null pointer.
///
/// # Safety
/// `ptr` must be valid for reads of `len` bytes if non-null, and the memory
/// must not be mutated for the duration of the borrow.
unsafe fn slice_from_raw(ptr: *const u8, len: usize) -> &'static [u8] {
    if ptr.is_null() || len == 0 {
        &[]
    } else {
        std::slice::from_raw_parts(ptr, len)
    }
}

/// Leak a `Vec<u8>` into a raw buffer that the caller (Swift, via
/// `anki_free`) now owns, writing the pointer/length pair into `out_ptr` /
/// `out_len`.
///
/// # Safety
/// `out_ptr` and `out_len` must be valid, non-null, writable pointers.
unsafe fn leak_into_out(bytes: Vec<u8>, out_ptr: *mut *mut u8, out_len: *mut usize) {
    let mut boxed = bytes.into_boxed_slice();
    let len = boxed.len();
    let ptr = boxed.as_mut_ptr();
    std::mem::forget(boxed);
    *out_ptr = ptr;
    *out_len = len;
}

/// Initialize a new backend from a serialized `anki_proto::backend::BackendInit`
/// message (same bytes the Python bridge decodes).
///
/// Returns a non-null handle on success. Returns `NULL` on failure (the
/// reason is logged to stderr; there is no payload to inspect).
///
/// Ownership: Swift owns the returned handle and must free it exactly once
/// via [`anki_free_backend`].
///
/// # Safety
/// `init_msg` must be valid for reads of `init_msg_len` bytes, or null if
/// `init_msg_len == 0`.
#[no_mangle]
pub extern "C" fn anki_open(init_msg: *const u8, init_msg_len: usize) -> *mut AnkiBackend {
    let bytes = unsafe { slice_from_raw(init_msg, init_msg_len) };
    match init_backend(bytes) {
        Ok(backend) => Box::into_raw(Box::new(AnkiBackend(backend))),
        Err(e) => {
            eprintln!("anki_open: failed to init backend: {e}");
            std::ptr::null_mut()
        }
    }
}

/// Invoke one `(service, method)` RPC against `backend`, with the same
/// semantics as `Backend::run_service_method`.
///
/// `input` is borrowed: Swift-owned, read-only for the duration of the call;
/// Rust never frees or retains it.
///
/// `out_ptr`/`out_len` are OUT parameters. They are always populated when
/// the return value is `0` or `1`; the returned buffer is heap-allocated by
/// Rust and must be released by Swift via exactly one call to
/// [`anki_free`] (never Swift's native `free`).
///
/// Return value:
/// - `0`  = success; `*out_ptr`/`*out_len` contain the serialized response
///   protobuf bytes.
/// - `1`  = RPC error; `*out_ptr`/`*out_len` contain the serialized
///   `anki_proto::backend::BackendError` bytes (identical format to
///   `rust_interface.rs`'s generated error branch).
/// - `-1` = handle/argument error (`backend` is null, or `out_ptr`/`out_len`
///   are null). `*out_ptr`/`*out_len` are left untouched — do not read them.
///
/// # Safety
/// `backend` must be a live pointer previously returned by [`anki_open`] and
/// not yet passed to [`anki_free_backend`]. `input` must be valid for reads
/// of `input_len` bytes, or null if `input_len == 0`. `out_ptr`/`out_len`
/// must be valid, non-null, writable pointers unless this function returns
/// `-1` before touching them (guaranteed here to happen before any write).
/// The caller MUST serialize all calls to this function for the same
/// `backend` handle; `Collection` is not thread-safe.
#[no_mangle]
pub extern "C" fn anki_command(
    backend: *mut AnkiBackend,
    service: u32,
    method: u32,
    input: *const u8,
    input_len: usize,
    out_ptr: *mut *mut u8,
    out_len: *mut usize,
) -> i32 {
    if backend.is_null() || out_ptr.is_null() || out_len.is_null() {
        return -1;
    }

    let backend_ref = unsafe { &(*backend).0 };
    let input_slice = unsafe { slice_from_raw(input, input_len) };

    match backend_ref.run_service_method(service, method, input_slice) {
        Ok(out_bytes) => {
            unsafe { leak_into_out(out_bytes, out_ptr, out_len) };
            0
        }
        Err(err_bytes) => {
            unsafe { leak_into_out(err_bytes, out_ptr, out_len) };
            1
        }
    }
}

/// Release a buffer previously written to `*out_ptr`/`*out_len` by
/// [`anki_command`]. Must be called exactly once per producing call.
/// `ptr == NULL` or `len == 0` is a no-op.
///
/// # Safety
/// `ptr` must either be null, or a pointer previously returned via
/// `anki_command`'s `out_ptr` together with its matching `len`, not already
/// freed.
#[no_mangle]
pub extern "C" fn anki_free(ptr: *mut u8, len: usize) {
    if ptr.is_null() || len == 0 {
        return;
    }
    unsafe {
        drop(Vec::from_raw_parts(ptr, len, len));
    }
}

/// Release a handle previously returned by [`anki_open`]. Must be called
/// exactly once; no [`anki_command`] call may follow it for this handle.
/// `backend == NULL` is a no-op.
///
/// # Safety
/// `backend` must either be null, or a pointer previously returned by
/// `anki_open`, not already freed, and not concurrently in use by another
/// call.
#[no_mangle]
pub extern "C" fn anki_free_backend(backend: *mut AnkiBackend) {
    if backend.is_null() {
        return;
    }
    unsafe {
        drop(Box::from_raw(backend));
    }
}
