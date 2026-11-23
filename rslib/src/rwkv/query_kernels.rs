// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! NEON kernels for the per-row parts of RWKV query scoring.
//!
//! Every kernel here reproduces the floating-point operation sequence of the
//! scalar code it replaces, so results are bit-identical: sequential
//! reductions keep their order by placing independent rows or heads in
//! separate vector lanes (after a 4x4 transpose) rather than splitting one
//! reduction across lanes, and no multiply/add pair is fused unless the
//! scalar code already uses a fused multiply-add.

// Only the macOS batched query path uses the row kernels outside tests.
#![cfg_attr(not(any(target_os = "macos", test)), allow(dead_code))]

use std::arch::aarch64::*;

use super::HEADS;
use super::HEAD_SIZE;

// The head kernels place one head per vector lane.
const _: () = assert!(HEADS == 4 && HEAD_SIZE.is_multiple_of(16));

/// Transposes a 4x4 block held as four row vectors into four column vectors.
#[inline(always)]
unsafe fn transpose4(
    a: float32x4_t,
    b: float32x4_t,
    c: float32x4_t,
    d: float32x4_t,
) -> [float32x4_t; 4] {
    let ab_even = vtrn1q_f32(a, b);
    let ab_odd = vtrn2q_f32(a, b);
    let cd_even = vtrn1q_f32(c, d);
    let cd_odd = vtrn2q_f32(c, d);
    [
        vreinterpretq_f32_f64(vtrn1q_f64(
            vreinterpretq_f64_f32(ab_even),
            vreinterpretq_f64_f32(cd_even),
        )),
        vreinterpretq_f32_f64(vtrn1q_f64(
            vreinterpretq_f64_f32(ab_odd),
            vreinterpretq_f64_f32(cd_odd),
        )),
        vreinterpretq_f32_f64(vtrn2q_f64(
            vreinterpretq_f64_f32(ab_even),
            vreinterpretq_f64_f32(cd_even),
        )),
        vreinterpretq_f32_f64(vtrn2q_f64(
            vreinterpretq_f64_f32(ab_odd),
            vreinterpretq_f64_f32(cd_odd),
        )),
    ]
}

/// Loads `values[lane * stride + offset..+4]` for four lanes and transposes
/// them, so element `i` of the result holds position `offset + i` of every
/// lane.
#[inline(always)]
unsafe fn load_transposed(values: *const f32, stride: usize, offset: usize) -> [float32x4_t; 4] {
    transpose4(
        vld1q_f32(values.add(offset)),
        vld1q_f32(values.add(stride + offset)),
        vld1q_f32(values.add(2 * stride + offset)),
        vld1q_f32(values.add(3 * stride + offset)),
    )
}

/// NEON form of `single_timestep_query_fast_scalar()`.
///
/// Each state row is loaded once for both of its dot products, and the two
/// products keep `dot_product_neon()`'s accumulator layout. Four rows are
/// reduced together with pairwise adds, which match `vaddvq_f32()` lane for
/// lane.
#[allow(clippy::too_many_arguments)]
#[inline(always)]
pub(super) unsafe fn single_timestep_query_fast_neon(
    r: &[f32],
    k: &[f32],
    v: &[f32],
    w: &[f32],
    a: &[f32],
    k_deformed: &[f32],
    state: Option<&[f32]>,
    out: &mut [f32],
) {
    let zero = vdupq_n_f32(0.0);
    for head in 0..HEADS {
        let head_base = head * HEAD_SIZE;
        let receptance = &r[head_base..head_base + HEAD_SIZE];
        let key = &k[head_base..head_base + HEAD_SIZE];
        let key_receptance = super::dot_product(key, receptance);
        let key_receptance_lanes = vdupq_n_f32(key_receptance);
        let v_ptr = v.as_ptr().add(head_base);
        let out_ptr = out.as_mut_ptr().add(head_base);

        let Some(state) = state else {
            for row in (0..HEAD_SIZE).step_by(4) {
                vst1q_f32(
                    out_ptr.add(row),
                    vmulq_f32(vld1q_f32(v_ptr.add(row)), key_receptance_lanes),
                );
            }
            continue;
        };

        let mut adaptation_receptance = 0.0;
        let mut key_deformed_lanes = [zero; HEAD_SIZE / 4];
        let mut decayed_receptance = [zero; HEAD_SIZE / 4];
        for block in 0..HEAD_SIZE / 4 {
            let offset = head_base + 4 * block;
            key_deformed_lanes[block] = vld1q_f32(k_deformed.as_ptr().add(offset));
            decayed_receptance[block] = vmulq_f32(
                vld1q_f32(w.as_ptr().add(offset)),
                vld1q_f32(r.as_ptr().add(offset)),
            );
        }
        for column in 0..HEAD_SIZE {
            let channel = head_base + column;
            adaptation_receptance += a[channel] * k_deformed[channel] * r[channel];
        }
        let adaptation_lanes = vdupq_n_f32(adaptation_receptance);

        let matrix = state.as_ptr().add(head * HEAD_SIZE * HEAD_SIZE);
        for row_base in (0..HEAD_SIZE).step_by(4) {
            let mut key_sums = [zero; 4];
            let mut retained_sums = [zero; 4];
            for lane in 0..4 {
                let row = matrix.add((row_base + lane) * HEAD_SIZE);
                let mut key_acc = [zero; 4];
                let mut retained_acc = [zero; 4];
                for block in 0..HEAD_SIZE / 4 {
                    let values = vld1q_f32(row.add(4 * block));
                    let acc = block % 4;
                    key_acc[acc] = vfmaq_f32(key_acc[acc], values, key_deformed_lanes[block]);
                    retained_acc[acc] =
                        vfmaq_f32(retained_acc[acc], values, decayed_receptance[block]);
                }
                key_sums[lane] = vaddq_f32(
                    vaddq_f32(key_acc[0], key_acc[1]),
                    vaddq_f32(key_acc[2], key_acc[3]),
                );
                retained_sums[lane] = vaddq_f32(
                    vaddq_f32(retained_acc[0], retained_acc[1]),
                    vaddq_f32(retained_acc[2], retained_acc[3]),
                );
            }
            let state_dot_key = vpaddq_f32(
                vpaddq_f32(key_sums[0], key_sums[1]),
                vpaddq_f32(key_sums[2], key_sums[3]),
            );
            let retained = vpaddq_f32(
                vpaddq_f32(retained_sums[0], retained_sums[1]),
                vpaddq_f32(retained_sums[2], retained_sums[3]),
            );
            let value = vaddq_f32(
                vsubq_f32(retained, vmulq_f32(state_dot_key, adaptation_lanes)),
                vmulq_f32(vld1q_f32(v_ptr.add(row_base)), key_receptance_lanes),
            );
            vst1q_f32(out_ptr.add(row_base), value);
        }
    }
}

/// Sequential per-head sums of one packed row of per-channel terms, starting
/// from `initial`, with the four heads in separate lanes.
#[inline(always)]
unsafe fn head_sums(initial: f32, values: *const f32) -> float32x4_t {
    let mut sums = vdupq_n_f32(initial);
    for offset in (0..HEAD_SIZE).step_by(4) {
        for column in load_transposed(values, HEAD_SIZE, offset) {
            sums = vaddq_f32(sums, column);
        }
    }
    sums
}

/// NEON form of `normalize_heads_in_place()` followed by
/// `scale_heads_in_place()` for one row.
#[inline(always)]
pub(super) unsafe fn normalize_scale_heads_neon(values: &mut [f32], scales: &[f32]) {
    debug_assert_eq!(values.len(), HEADS * HEAD_SIZE);
    debug_assert_eq!(scales.len(), HEADS);
    let ptr = values.as_mut_ptr();
    let mut squares = [0.0f32; HEADS * HEAD_SIZE];
    for offset in (0..HEADS * HEAD_SIZE).step_by(4) {
        let value = vld1q_f32(ptr.add(offset));
        vst1q_f32(squares.as_mut_ptr().add(offset), vmulq_f32(value, value));
    }
    let norms = vmaxnmq_f32(
        vsqrtq_f32(head_sums(-0.0, squares.as_ptr())),
        vdupq_n_f32(1e-12),
    );
    let mut head_norms = [0.0f32; HEADS];
    vst1q_f32(head_norms.as_mut_ptr(), norms);
    for head in 0..HEADS {
        let norm = vdupq_n_f32(head_norms[head]);
        let scale = vdupq_n_f32(scales[head]);
        for offset in (head * HEAD_SIZE..(head + 1) * HEAD_SIZE).step_by(4) {
            let value = vdivq_f32(vld1q_f32(ptr.add(offset)), norm);
            vst1q_f32(ptr.add(offset), vmulq_f32(value, scale));
        }
    }
}

/// NEON form of the per-head bonus and gate applied to the group-normed
/// recurrence output of one row:
/// `output = g * (output + sum(r * bonus * k) * v)`, with the sum per head.
#[inline(always)]
pub(super) unsafe fn bonus_gate_neon(
    output: &mut [f32],
    r: &[f32],
    bonus: &[f32],
    k: &[f32],
    v: &[f32],
    g: &[f32],
) {
    let len = HEADS * HEAD_SIZE;
    debug_assert!(
        output.len() == len
            && r.len() == len
            && bonus.len() == len
            && k.len() == len
            && v.len() == len
            && g.len() == len
    );
    let mut terms = [0.0f32; HEADS * HEAD_SIZE];
    for offset in (0..len).step_by(4) {
        let term = vmulq_f32(
            vmulq_f32(
                vld1q_f32(r.as_ptr().add(offset)),
                vld1q_f32(bonus.as_ptr().add(offset)),
            ),
            vld1q_f32(k.as_ptr().add(offset)),
        );
        vst1q_f32(terms.as_mut_ptr().add(offset), term);
    }
    let mut bonus_scales = [0.0f32; HEADS];
    vst1q_f32(bonus_scales.as_mut_ptr(), head_sums(0.0, terms.as_ptr()));
    let out_ptr = output.as_mut_ptr();
    for (head, bonus_scale) in bonus_scales.into_iter().enumerate() {
        let bonus_scale = vdupq_n_f32(bonus_scale);
        for offset in (head * HEAD_SIZE..(head + 1) * HEAD_SIZE).step_by(4) {
            let value = vmulq_f32(
                vld1q_f32(g.as_ptr().add(offset)),
                vaddq_f32(
                    vld1q_f32(out_ptr.add(offset)),
                    vmulq_f32(bonus_scale, vld1q_f32(v.as_ptr().add(offset))),
                ),
            );
            vst1q_f32(out_ptr.add(offset), value);
        }
    }
}

/// NEON form of `Norm::apply_batch()` for `4 * BLOCKS` rows, with one row per
/// vector lane. `group_size` must be a multiple of four.
#[allow(clippy::too_many_arguments)]
#[inline(always)]
pub(super) unsafe fn norm_rows_neon<const BLOCKS: usize>(
    input: *const f32,
    output: *mut f32,
    dim: usize,
    groups: usize,
    eps: f32,
    weight: &[f32],
    bias: &[f32],
) {
    let group_size = dim / groups;
    debug_assert_eq!(group_size % 4, 0);
    let divisor = vdupq_n_f32(group_size as f32);
    let eps = vdupq_n_f32(eps);
    let one = vdupq_n_f32(1.0);
    for group in 0..groups {
        let start = group * group_size;
        let end = start + group_size;
        let mut means = [vdupq_n_f32(-0.0); BLOCKS];
        for channel in (start..end).step_by(4) {
            for (block, mean) in means.iter_mut().enumerate() {
                let rows = input.add(4 * block * dim);
                for column in load_transposed(rows, dim, channel) {
                    *mean = vaddq_f32(*mean, column);
                }
            }
        }
        for mean in &mut means {
            *mean = vdivq_f32(*mean, divisor);
        }
        let mut scales = [vdupq_n_f32(-0.0); BLOCKS];
        for channel in (start..end).step_by(4) {
            for (block, scale) in scales.iter_mut().enumerate() {
                let rows = input.add(4 * block * dim);
                for column in load_transposed(rows, dim, channel) {
                    let diff = vsubq_f32(column, means[block]);
                    *scale = vaddq_f32(*scale, vmulq_f32(diff, diff));
                }
            }
        }
        for scale in &mut scales {
            *scale = vdivq_f32(one, vsqrtq_f32(vaddq_f32(vdivq_f32(*scale, divisor), eps)));
        }
        let mut row_means = [[0.0f32; 4]; BLOCKS];
        let mut row_scales = [[0.0f32; 4]; BLOCKS];
        for block in 0..BLOCKS {
            vst1q_f32(row_means[block].as_mut_ptr(), means[block]);
            vst1q_f32(row_scales[block].as_mut_ptr(), scales[block]);
        }
        for block in 0..BLOCKS {
            for lane in 0..4 {
                let row = 4 * block + lane;
                let mean = vdupq_n_f32(row_means[block][lane]);
                let scale = vdupq_n_f32(row_scales[block][lane]);
                for channel in (start..end).step_by(4) {
                    let index = row * dim + channel;
                    let value = vaddq_f32(
                        vmulq_f32(
                            vmulq_f32(vsubq_f32(vld1q_f32(input.add(index)), mean), scale),
                            vld1q_f32(weight.as_ptr().add(channel)),
                        ),
                        vld1q_f32(bias.as_ptr().add(channel)),
                    );
                    vst1q_f32(output.add(index), value);
                }
            }
        }
    }
}
