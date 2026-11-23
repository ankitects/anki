// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

const CBLAS_ROW_MAJOR: i32 = 101;
const CBLAS_NO_TRANS: i32 = 111;

#[link(name = "Accelerate", kind = "framework")]
extern "C" {
    fn cblas_sgemm(
        order: i32,
        trans_a: i32,
        trans_b: i32,
        m: i32,
        n: i32,
        k: i32,
        alpha: f32,
        a: *const f32,
        lda: i32,
        b: *const f32,
        ldb: i32,
        beta: f32,
        c: *mut f32,
        ldc: i32,
    );
}

pub(super) fn matrix_times_matrix(
    left: &[f32],
    right: &[f32],
    rows: usize,
    columns: usize,
    shared: usize,
    out: &mut [f32],
) {
    let left_len = rows.checked_mul(shared).expect("left matrix is too large");
    let right_len = columns
        .checked_mul(shared)
        .expect("right matrix is too large");
    let output_len = rows
        .checked_mul(columns)
        .expect("output matrix is too large");
    assert_eq!(left.len(), left_len);
    assert_eq!(right.len(), right_len);
    assert_eq!(out.len(), output_len);
    let rows = i32::try_from(rows).expect("row count exceeds CBLAS limits");
    let columns = i32::try_from(columns).expect("column count exceeds CBLAS limits");
    let shared = i32::try_from(shared).expect("shared dimension exceeds CBLAS limits");

    // SAFETY: all matrices are contiguous row-major f32 slices with the
    // dimensions and leading strides provided below.
    unsafe {
        cblas_sgemm(
            CBLAS_ROW_MAJOR,
            CBLAS_NO_TRANS,
            CBLAS_NO_TRANS,
            rows,
            columns,
            shared,
            1.0,
            left.as_ptr(),
            shared,
            right.as_ptr(),
            columns,
            0.0,
            out.as_mut_ptr(),
            columns,
        );
    }
}

#[link(name = "Accelerate", kind = "framework")]
extern "C" {
    fn vvexpf(y: *mut f32, x: *const f32, n: *const i32);
    fn vvtanhf(y: *mut f32, x: *const f32, n: *const i32);
}

/// Stack chunk used by the vectorized activations, so they never allocate.
const ACTIVATION_CHUNK: usize = 1024;

/// Applies `exp` to every element of `input`, writing into `output`.
fn exp_into(input: &[f32], output: &mut [f32]) {
    assert_eq!(input.len(), output.len());
    let len = i32::try_from(input.len()).expect("activation chunk exceeds vForce limits");
    // SAFETY: both slices hold `len` contiguous f32 values.
    unsafe { vvexpf(output.as_mut_ptr(), input.as_ptr(), &len) };
}

/// `tanh` over every element, using Accelerate's vectorized implementation.
pub(super) fn tanh_in_place(values: &mut [f32]) {
    let mut input = [0.0f32; ACTIVATION_CHUNK];
    for chunk in values.chunks_mut(ACTIVATION_CHUNK) {
        let input = &mut input[..chunk.len()];
        input.copy_from_slice(chunk);
        let len = i32::try_from(chunk.len()).expect("activation chunk exceeds vForce limits");
        // SAFETY: both slices hold `len` contiguous f32 values.
        unsafe { vvtanhf(chunk.as_mut_ptr(), input.as_ptr(), &len) };
    }
}

/// The logistic function over every element. Uses the same `exp(-|x|)`
/// formulation as the scalar `sigmoid()`, with a vectorized `exp`.
pub(super) fn sigmoid_in_place(values: &mut [f32]) {
    let mut negative_magnitude = [0.0f32; ACTIVATION_CHUNK];
    let mut exp = [0.0f32; ACTIVATION_CHUNK];
    for chunk in values.chunks_mut(ACTIVATION_CHUNK) {
        let len = chunk.len();
        for (target, value) in negative_magnitude[..len].iter_mut().zip(chunk.iter()) {
            *target = -value.abs();
        }
        exp_into(&negative_magnitude[..len], &mut exp[..len]);
        for (value, exp) in chunk.iter_mut().zip(&exp[..len]) {
            *value = if *value >= 0.0 {
                1.0 / (1.0 + exp)
            } else {
                exp / (1.0 + exp)
            };
        }
    }
}

/// `x * sigmoid(x)` over every element.
pub(super) fn silu_in_place(values: &mut [f32]) {
    let mut gates = [0.0f32; ACTIVATION_CHUNK];
    for chunk in values.chunks_mut(ACTIVATION_CHUNK) {
        let gates = &mut gates[..chunk.len()];
        gates.copy_from_slice(chunk);
        sigmoid_in_place(gates);
        for (value, gate) in chunk.iter_mut().zip(gates.iter()) {
            *value *= gate;
        }
    }
}

/// `exp(-scale * sigmoid(x))` over every element.
pub(super) fn scaled_sigmoid_decay_in_place(values: &mut [f32], scale: f32) {
    let mut exponent = [0.0f32; ACTIVATION_CHUNK];
    for chunk in values.chunks_mut(ACTIVATION_CHUNK) {
        sigmoid_in_place(chunk);
        let exponent = &mut exponent[..chunk.len()];
        for (target, value) in exponent.iter_mut().zip(chunk.iter()) {
            *target = -scale * value;
        }
        exp_into(exponent, chunk);
    }
}
