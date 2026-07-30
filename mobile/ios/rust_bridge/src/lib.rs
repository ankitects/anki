// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Stable C boundary for embedding Anki's Rust backend in the iOS companion.
//!
//! Swift receives opaque numeric handles rather than Rust pointers. A registry
//! owns handles and buffers, while per-handle call leases serialize operations,
//! allow concurrent progress reads, and make repeated close/free calls harmless.

use std::{
    any::Any,
    collections::HashMap,
    panic::{catch_unwind, AssertUnwindSafe},
    ptr, slice,
    sync::{Arc, Condvar, Mutex, MutexGuard, OnceLock},
};

use anki::backend::{init_backend, Backend};
use anki_proto::backend::{backend_error, BackendError};
use prost::Message;

/// Exact repository revision compiled into this bridge.
///
/// A `-dirty` suffix records tracked changes present when the artifact was
/// built. The build script owns this value so every bridge entry point and
/// consumer observes the identity of the linked native library.
pub const SOURCE_REVISION: &str = env!("ANKI_IOS_SOURCE_REVISION");
static SOURCE_REVISION_C: &str = concat!(env!("ANKI_IOS_SOURCE_REVISION"), "\0");

#[repr(C)]
#[derive(Clone, Copy, Debug)]
pub struct AnkiByteSlice {
    pub data: *const u8,
    pub len: usize,
}

impl AnkiByteSlice {
    /// # Safety
    ///
    /// For a non-empty slice, `data` must point to `len` readable bytes for the
    /// duration of the bridge call.
    unsafe fn as_bytes<'a>(self) -> Result<&'a [u8], Vec<u8>> {
        if self.len == 0 {
            return Ok(&[]);
        }
        if self.data.is_null() {
            return Err(serialized_error(
                backend_error::Kind::InvalidInput,
                "non-empty input used a null data pointer",
            ));
        }
        // SAFETY: upheld by the caller contract documented above.
        Ok(unsafe { slice::from_raw_parts(self.data, self.len) })
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug)]
pub struct AnkiOwnedBuffer {
    pub data: *const u8,
    pub len: usize,
    pub token: u64,
}

impl AnkiOwnedBuffer {
    const EMPTY: Self = Self {
        data: ptr::null(),
        len: 0,
        token: 0,
    };
}

#[repr(C)]
#[derive(Clone, Copy, Debug)]
pub struct AnkiBackendOpenResult {
    pub handle: u64,
    pub error: AnkiOwnedBuffer,
}

#[repr(C)]
#[derive(Clone, Copy, Debug)]
pub struct AnkiBackendCallResult {
    pub output: AnkiOwnedBuffer,
    pub error: AnkiOwnedBuffer,
}

struct BackendEntry {
    backend: Backend,
    lifecycle: Mutex<BackendLifecycle>,
    lifecycle_changed: Condvar,
    serialized_dispatch: Mutex<()>,
}

#[derive(Default)]
struct BackendLifecycle {
    active_calls: usize,
    closing: bool,
}

impl BackendEntry {
    fn new(backend: Backend) -> Self {
        Self {
            backend,
            lifecycle: Mutex::new(BackendLifecycle::default()),
            lifecycle_changed: Condvar::new(),
            serialized_dispatch: Mutex::new(()),
        }
    }

    fn begin_call(&self) -> Result<BackendCallLease<'_>, Vec<u8>> {
        let mut lifecycle = self
            .lifecycle
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        if lifecycle.closing {
            return Err(serialized_error(
                backend_error::Kind::InvalidInput,
                "backend handle is closing",
            ));
        }
        lifecycle.active_calls += 1;
        Ok(BackendCallLease { entry: self })
    }

    fn lock_serialized_dispatch(&self) -> MutexGuard<'_, ()> {
        self.serialized_dispatch
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
    }

    fn close(&self) {
        let mut lifecycle = self
            .lifecycle
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        lifecycle.closing = true;
        while lifecycle.active_calls != 0 {
            lifecycle = self
                .lifecycle_changed
                .wait(lifecycle)
                .unwrap_or_else(|poisoned| poisoned.into_inner());
        }
    }
}

struct BackendCallLease<'a> {
    entry: &'a BackendEntry,
}

impl Drop for BackendCallLease<'_> {
    fn drop(&mut self) {
        let mut lifecycle = self
            .entry
            .lifecycle
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        lifecycle.active_calls -= 1;
        if lifecycle.active_calls == 0 {
            self.entry.lifecycle_changed.notify_all();
        }
    }
}

struct BridgeState {
    backends: HashMap<u64, Arc<BackendEntry>>,
    buffers: HashMap<u64, Box<[u8]>>,
    next_handle: u64,
    next_buffer: u64,
}

impl Default for BridgeState {
    fn default() -> Self {
        Self {
            backends: HashMap::new(),
            buffers: HashMap::new(),
            next_handle: 1,
            next_buffer: 1,
        }
    }
}

impl BridgeState {
    fn insert_backend(&mut self, backend: Backend) -> u64 {
        let handle = next_available_id(&mut self.next_handle, &self.backends);
        self.backends
            .insert(handle, Arc::new(BackendEntry::new(backend)));
        handle
    }

    fn store_buffer(&mut self, bytes: Vec<u8>) -> AnkiOwnedBuffer {
        if bytes.is_empty() {
            return AnkiOwnedBuffer::EMPTY;
        }

        let token = next_available_id(&mut self.next_buffer, &self.buffers);
        let bytes = bytes.into_boxed_slice();
        let buffer = AnkiOwnedBuffer {
            data: bytes.as_ptr(),
            len: bytes.len(),
            token,
        };
        self.buffers.insert(token, bytes);
        buffer
    }
}

fn next_available_id<T>(next: &mut u64, occupied: &HashMap<u64, T>) -> u64 {
    loop {
        let candidate = *next;
        *next = next.wrapping_add(1);
        if candidate != 0 && !occupied.contains_key(&candidate) {
            return candidate;
        }
    }
}

fn state() -> &'static Mutex<BridgeState> {
    static STATE: OnceLock<Mutex<BridgeState>> = OnceLock::new();
    STATE.get_or_init(|| Mutex::new(BridgeState::default()))
}

fn lock_state() -> MutexGuard<'static, BridgeState> {
    // Recover the registry after a contained panic. No Rust unwind is allowed
    // to cross the C boundary, and numeric handles remain memory-safe even if a
    // backend operation panics.
    state()
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner())
}

fn serialized_error(kind: backend_error::Kind, message: impl Into<String>) -> Vec<u8> {
    BackendError {
        kind: kind as i32,
        message: message.into(),
        ..Default::default()
    }
    .encode_to_vec()
}

fn panic_error(panic: Box<dyn Any + Send>) -> Vec<u8> {
    let message = if let Some(message) = panic.downcast_ref::<&'static str>() {
        *message
    } else if let Some(message) = panic.downcast_ref::<String>() {
        message.as_str()
    } else {
        "unknown Rust panic"
    };
    serialized_error(
        backend_error::Kind::AnkidroidPanicError,
        format!("Rust backend panic: {message}"),
    )
}

fn catch_backend<T>(operation: impl FnOnce() -> Result<T, Vec<u8>>) -> Result<T, Vec<u8>> {
    match catch_unwind(AssertUnwindSafe(operation)) {
        Ok(result) => result,
        Err(panic) => Err(panic_error(panic)),
    }
}

fn store_buffer(bytes: Vec<u8>) -> AnkiOwnedBuffer {
    lock_state().store_buffer(bytes)
}

/// Return the source revision compiled into this bridge.
///
/// The returned NUL-terminated string is static and remains valid for the
/// lifetime of the process. Callers must not modify or free it.
#[no_mangle]
pub extern "C" fn anki_backend_source_revision() -> *const std::ffi::c_char {
    SOURCE_REVISION_C.as_ptr().cast()
}

/// Open an Anki backend and return a registry-owned opaque handle.
///
/// # Safety
///
/// `input` must satisfy [`AnkiByteSlice::as_bytes`]'s pointer contract.
#[no_mangle]
pub unsafe extern "C" fn anki_backend_open(input: AnkiByteSlice) -> AnkiBackendOpenResult {
    let result = catch_backend(|| {
        // SAFETY: delegated from this function's caller contract.
        let input = unsafe { input.as_bytes()? };
        let backend = init_backend(input)
            .map_err(|message| serialized_error(backend_error::Kind::InvalidInput, message))?;
        Ok(lock_state().insert_backend(backend))
    });

    match result {
        Ok(handle) => AnkiBackendOpenResult {
            handle,
            error: AnkiOwnedBuffer::EMPTY,
        },
        Err(error) => AnkiBackendOpenResult {
            handle: 0,
            error: store_buffer(error),
        },
    }
}

/// Close a backend handle. Closing an unknown or already-closed handle returns
/// a serialized `BackendError`; it never dereferences caller-provided memory.
#[no_mangle]
pub extern "C" fn anki_backend_close(handle: u64) -> AnkiOwnedBuffer {
    let result = catch_backend(|| {
        let entry = lock_state().backends.remove(&handle).ok_or_else(|| {
            serialized_error(
                backend_error::Kind::InvalidInput,
                format!("unknown or stale backend handle: {handle}"),
            )
        })?;
        entry.close();
        Ok(())
    });

    result
        .err()
        .map(store_buffer)
        .unwrap_or(AnkiOwnedBuffer::EMPTY)
}

/// Run a protobuf service method against an open backend.
///
/// Calls other than `LatestProgress` are serialized per backend. Progress reads
/// can run beside a long operation. Closing removes the handle first and then
/// waits for all in-flight calls, so no dispatch can outlive its backend.
/// Exactly one of `output` and `error` is populated. Empty successful protobuf
/// responses use the zero-token empty buffer.
///
/// # Safety
///
/// `input` must satisfy [`AnkiByteSlice::as_bytes`]'s pointer contract.
#[no_mangle]
pub unsafe extern "C" fn anki_backend_run_method(
    handle: u64,
    service: u32,
    method: u32,
    input: AnkiByteSlice,
) -> AnkiBackendCallResult {
    let result = catch_backend(|| {
        // SAFETY: delegated from this function's caller contract.
        let input = unsafe { input.as_bytes()? };
        let entry = lock_state().backends.get(&handle).cloned().ok_or_else(|| {
            serialized_error(
                backend_error::Kind::InvalidInput,
                format!("unknown or stale backend handle: {handle}"),
            )
        })?;
        let _lease = entry.begin_call()?;
        if is_latest_progress(service, method) {
            entry.backend.run_service_method(service, method, input)
        } else {
            let _dispatch = entry.lock_serialized_dispatch();
            entry.backend.run_service_method(service, method, input)
        }
    });

    match result {
        Ok(output) => AnkiBackendCallResult {
            output: store_buffer(output),
            error: AnkiOwnedBuffer::EMPTY,
        },
        Err(error) => AnkiBackendCallResult {
            output: AnkiOwnedBuffer::EMPTY,
            error: store_buffer(error),
        },
    }
}

fn is_latest_progress(service: u32, method: u32) -> bool {
    service == 3 && method == 4
}

/// Release a bridge-owned result or error buffer.
///
/// The ownership token, rather than a caller-provided pointer, identifies the
/// allocation. Zero, unknown, and previously released tokens are ignored.
#[no_mangle]
pub extern "C" fn anki_backend_buffer_free(buffer: AnkiOwnedBuffer) {
    if buffer.token != 0 {
        lock_state().buffers.remove(&buffer.token);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::{sync::mpsc, thread, time::Duration};

    #[test]
    fn catches_panics_as_serialized_backend_errors() {
        let result = catch_backend::<()>(|| panic!("contained test panic"));
        let error = BackendError::decode(result.unwrap_err().as_slice()).unwrap();

        assert_eq!(error.kind, backend_error::Kind::AnkidroidPanicError as i32);
        assert!(error.message.contains("contained test panic"));
    }

    #[test]
    fn local_registry_ignores_repeated_buffer_release_tokens() {
        let mut state = BridgeState::default();
        let buffer = state.store_buffer(vec![1, 2, 3]);
        assert!(state.buffers.remove(&buffer.token).is_some());
        assert!(state.buffers.remove(&buffer.token).is_none());
    }

    #[test]
    fn progress_bypasses_dispatch_serialization_while_close_waits_for_call_leases() {
        let backend = init_backend(&[]).unwrap();
        let entry = Arc::new(BackendEntry::new(backend));
        let long_call = entry.begin_call().unwrap();
        let serialized_dispatch = entry.lock_serialized_dispatch();

        assert!(is_latest_progress(3, 4));
        let progress_call = entry.begin_call().unwrap();

        let closing_entry = Arc::clone(&entry);
        let (started_tx, started_rx) = mpsc::channel();
        let (closed_tx, closed_rx) = mpsc::channel();
        let closer = thread::spawn(move || {
            started_tx.send(()).unwrap();
            closing_entry.close();
            closed_tx.send(()).unwrap();
        });
        started_rx.recv().unwrap();
        assert!(closed_rx.try_recv().is_err());

        drop(progress_call);
        assert!(closed_rx.try_recv().is_err());
        drop(serialized_dispatch);
        drop(long_call);

        closed_rx.recv_timeout(Duration::from_secs(1)).unwrap();
        closer.join().unwrap();
    }
}
