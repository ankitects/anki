use std::{ffi::CStr, ptr};

use anki_ios_bridge::{
    anki_backend_buffer_free, anki_backend_close, anki_backend_open, anki_backend_run_method,
    anki_backend_source_revision, AnkiBackendCallResult, AnkiBackendOpenResult, AnkiByteSlice,
    AnkiOwnedBuffer,
};
use anki_proto::{
    backend::{BackendError, BackendInit},
    card_rendering::StripHtmlRequest,
    generic::String as ProtoString,
};
use prost::Message;

fn slice(bytes: &[u8]) -> AnkiByteSlice {
    AnkiByteSlice {
        data: bytes.as_ptr(),
        len: bytes.len(),
    }
}

fn empty_slice() -> AnkiByteSlice {
    AnkiByteSlice {
        data: ptr::null(),
        len: 0,
    }
}

fn valid_init() -> Vec<u8> {
    BackendInit::default().encode_to_vec()
}

fn open(input: AnkiByteSlice) -> AnkiBackendOpenResult {
    // SAFETY: every test input is either a valid Rust slice or intentionally
    // null, which the bridge rejects before dereferencing.
    unsafe { anki_backend_open(input) }
}

fn run(handle: u64, service: u32, method: u32, input: AnkiByteSlice) -> AnkiBackendCallResult {
    // SAFETY: every non-empty test input points to a live Rust byte slice.
    unsafe { anki_backend_run_method(handle, service, method, input) }
}

fn copy_buffer(buffer: AnkiOwnedBuffer) -> Vec<u8> {
    if buffer.len == 0 {
        return Vec::new();
    }
    // SAFETY: bridge-owned buffers remain valid until explicitly released.
    unsafe { std::slice::from_raw_parts(buffer.data, buffer.len) }.to_vec()
}

fn assert_no_error(error: AnkiOwnedBuffer) {
    assert_eq!(error.token, 0, "unexpected bridge error");
    assert!(error.data.is_null());
    assert_eq!(error.len, 0);
}

#[test]
fn exposes_the_exact_source_revision_compiled_into_the_bridge() {
    let revision = unsafe { CStr::from_ptr(anki_backend_source_revision()) }
        .to_str()
        .unwrap();

    assert!(!revision.is_empty());
    assert_eq!(revision, anki_ios_bridge::SOURCE_REVISION);
}

#[test]
fn opens_and_closes_backend_repeatedly_without_reusing_stale_handles() {
    let init = valid_init();
    let mut previous_handle = 0;

    for _ in 0..100 {
        let opened = open(slice(&init));
        assert_ne!(opened.handle, 0);
        assert!(opened.handle > previous_handle);
        assert_no_error(opened.error);
        previous_handle = opened.handle;

        assert_no_error(anki_backend_close(opened.handle));
        let stale_error = anki_backend_close(opened.handle);
        let decoded = BackendError::decode(copy_buffer(stale_error).as_slice()).unwrap();
        assert!(decoded.message.contains("stale"));
        anki_backend_buffer_free(stale_error);
    }
}

#[test]
fn invalid_init_returns_serialized_error_without_a_handle() {
    let opened = open(slice(&[0xff]));

    assert_eq!(opened.handle, 0);
    let decoded = BackendError::decode(copy_buffer(opened.error).as_slice()).unwrap();
    assert!(decoded.message.contains("decode init"));
    anki_backend_buffer_free(opened.error);
}

#[test]
fn successful_dispatch_returns_one_owned_buffer_that_can_be_released_twice_safely() {
    let init = valid_init();
    let opened = open(slice(&init));
    assert_no_error(opened.error);

    let request = StripHtmlRequest {
        text: "<b>shared backend</b>".into(),
        mode: 0,
    }
    .encode_to_vec();
    let result = run(opened.handle, 27, 0, slice(&request));

    assert_no_error(result.error);
    let response = ProtoString::decode(copy_buffer(result.output).as_slice()).unwrap();
    assert_eq!(response.val, "shared backend");
    anki_backend_buffer_free(result.output);
    anki_backend_buffer_free(result.output);
    assert_no_error(anki_backend_close(opened.handle));
}

#[test]
fn invalid_or_stale_handles_return_errors_instead_of_dereferencing_pointers() {
    let stale = run(u64::MAX, 27, 0, empty_slice());

    assert_eq!(stale.output.token, 0);
    let decoded = BackendError::decode(copy_buffer(stale.error).as_slice()).unwrap();
    assert!(decoded.message.contains("stale"));
    anki_backend_buffer_free(stale.error);
}

#[test]
fn non_empty_null_input_is_rejected_at_the_boundary() {
    let opened = open(AnkiByteSlice {
        data: ptr::null(),
        len: 1,
    });

    assert_eq!(opened.handle, 0);
    let decoded = BackendError::decode(copy_buffer(opened.error).as_slice()).unwrap();
    assert!(decoded.message.contains("null"));
    anki_backend_buffer_free(opened.error);
}
