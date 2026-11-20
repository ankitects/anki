// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Offline int4/f32 projection kernel. Codes remain packed in RAM. NEON
//! unpacks sixteen weights directly into registers and reuses them across
//! four rows; no f32 projection buffer or integer activation quantizer exists.

use super::packed_weights::PackedLinear;
use super::weight_precision_bench::GROUP_SIZE;

#[derive(Clone)]
pub(super) struct FusedLinear {
    input: usize,
    output: usize,
    stride: usize,
    codes: Vec<u8>,
    scales: Vec<f32>,
}

impl FusedLinear {
    pub fn from_packed(packed: &PackedLinear) -> Self {
        let stride = packed.output.div_ceil(16) * 16;
        let mut codes = vec![0; packed.input * stride / 2];
        let mut scales = vec![0.0; packed.input.div_ceil(GROUP_SIZE) * stride];
        let mut offset = 0;
        for row in 0..packed.output {
            for column in (0..packed.input).step_by(GROUP_SIZE) {
                scales[column / GROUP_SIZE * stride + row] =
                    f32::from_le_bytes(packed.bytes[offset..offset + 4].try_into().unwrap());
                offset += 4;
                let count = (packed.input - column).min(GROUP_SIZE);
                for index in 0..count {
                    let code = (packed.bytes[offset + index / 2] >> (4 * (index % 2))) & 15;
                    // One eight-byte vector holds sixteen outputs: the low
                    // nibbles are lanes 0..7, the high nibbles lanes 8..15.
                    let destination = (column + index) * stride / 2 + row / 16 * 8 + row % 8;
                    codes[destination] |= code << (4 * (row % 16 / 8));
                }
                offset += count.div_ceil(2);
            }
        }
        assert_eq!(offset, packed.bytes.len());
        Self {
            input: packed.input,
            output: packed.output,
            stride,
            codes,
            scales,
        }
    }

    fn weight(&self, column: usize, output: usize) -> f32 {
        let packed = self.codes[column * self.stride / 2 + output / 16 * 8 + output % 8];
        let nibble = (packed >> (4 * (output % 16 / 8))) & 15;
        let code = ((nibble as i8) << 4) >> 4;
        code as f32 * self.scales[column / GROUP_SIZE * self.stride + output]
    }

    pub fn apply(&self, inputs: &[f32], outs: &mut [f32], rows: usize, bias: Option<&[f32]>) {
        assert_eq!(inputs.len(), rows * self.input);
        assert_eq!(outs.len(), rows * self.output);
        for out in outs.chunks_exact_mut(self.output) {
            if let Some(bias) = bias {
                out.copy_from_slice(bias);
            } else {
                out.fill(0.0);
            }
        }
        #[cfg(not(target_arch = "aarch64"))]
        let vector_end = 0;
        #[cfg(target_arch = "aarch64")]
        let vector_end = self.output / 16 * 16;
        #[cfg(target_arch = "aarch64")]
        {
            // SAFETY: NEON is mandatory on AArch64. The constructor pads code
            // columns and scale groups to sixteen outputs; only complete
            // output vectors and rows enter the kernel.
            unsafe {
                for row in (0..rows / 4 * 4).step_by(4) {
                    self.apply_neon::<4>(inputs, outs, row, vector_end);
                }
                for row in rows / 4 * 4..rows {
                    self.apply_neon::<1>(inputs, outs, row, vector_end);
                }
            }
        }
        // Small projections and partial output tiles need no padded output
        // buffer. The f32 path uses FMA for complete SIMD vectors and separate
        // multiply/add in its scalar tail; preserve that distinction exactly.
        for row in 0..rows {
            for output in vector_end..self.output {
                let value = &mut outs[row * self.output + output];
                for column in 0..self.input {
                    let activation = inputs[row * self.input + column];
                    if activation == 0.0 {
                        continue;
                    }
                    let weight = self.weight(column, output);
                    #[cfg(target_arch = "aarch64")]
                    if output < self.output / 4 * 4 {
                        *value = weight.mul_add(activation, *value);
                        continue;
                    }
                    #[cfg(target_arch = "x86_64")]
                    if super::x86_avx2_fma_available() && output < self.output / 8 * 8 {
                        *value = weight.mul_add(activation, *value);
                        continue;
                    }
                    *value += weight * activation;
                }
            }
        }
    }

    #[cfg(target_arch = "aarch64")]
    unsafe fn apply_neon<const ROWS: usize>(
        &self,
        inputs: &[f32],
        outs: &mut [f32],
        row: usize,
        end: usize,
    ) {
        use std::arch::aarch64::*;

        for output in (0..end).step_by(16) {
            let mut accumulators = [[vdupq_n_f32(0.0); 4]; ROWS];
            for (lane, accumulator) in accumulators.iter_mut().enumerate() {
                for (vector, value) in accumulator.iter_mut().enumerate() {
                    *value = vld1q_f32(
                        outs.as_ptr()
                            .add((row + lane) * self.output + output + vector * 4),
                    );
                }
            }
            for group in (0..self.input).step_by(GROUP_SIZE) {
                let scale = self
                    .scales
                    .as_ptr()
                    .add(group / GROUP_SIZE * self.stride + output);
                let scales = [
                    vld1q_f32(scale),
                    vld1q_f32(scale.add(4)),
                    vld1q_f32(scale.add(8)),
                    vld1q_f32(scale.add(12)),
                ];
                for column in group..(group + GROUP_SIZE).min(self.input) {
                    let activations: [f32; ROWS] =
                        std::array::from_fn(|lane| inputs[(row + lane) * self.input + column]);
                    if activations.iter().all(|&value| value == 0.0) {
                        continue;
                    }
                    let codes = vreinterpret_s8_u8(vld1_u8(
                        self.codes
                            .as_ptr()
                            .add(column * self.stride / 2 + output / 2),
                    ));
                    let low = vmovl_s8(vshr_n_s8::<4>(vshl_n_s8::<4>(codes)));
                    let high = vmovl_s8(vshr_n_s8::<4>(codes));
                    let weights = [
                        vmulq_f32(vcvtq_f32_s32(vmovl_s16(vget_low_s16(low))), scales[0]),
                        vmulq_f32(vcvtq_f32_s32(vmovl_s16(vget_high_s16(low))), scales[1]),
                        vmulq_f32(vcvtq_f32_s32(vmovl_s16(vget_low_s16(high))), scales[2]),
                        vmulq_f32(vcvtq_f32_s32(vmovl_s16(vget_high_s16(high))), scales[3]),
                    ];
                    for (accumulator, activation) in accumulators.iter_mut().zip(activations) {
                        if activation == 0.0 {
                            continue;
                        }
                        for (value, weight) in accumulator.iter_mut().zip(weights) {
                            *value = vfmaq_n_f32(*value, weight, activation);
                        }
                    }
                }
            }
            for (lane, accumulator) in accumulators.iter().enumerate() {
                for (vector, &value) in accumulator.iter().enumerate() {
                    vst1q_f32(
                        outs.as_mut_ptr()
                            .add((row + lane) * self.output + output + vector * 4),
                        value,
                    );
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::super::weight_precision_bench::decode_with_storage;
    use super::super::weight_precision_bench::encode;
    use super::super::weight_precision_bench::runtime_with_compute;
    use super::super::*;

    #[test]
    fn rwkv_weight_precision_fused_preserves_projection_outputs_at_group_and_tile_boundaries() {
        for (input, output) in [(5, 1), (33, 5), (64, 16), (65, 17), (128, 32)] {
            let weights = WeightMap {
                packed_linears: HashMap::new(),
                tensors: HashMap::from([
                    (
                        "test.weight".into(),
                        Tensor {
                            shape: vec![output, input],
                            values: (0..input * output)
                                .map(|i| (i as f32 * 0.3).sin())
                                .collect(),
                        },
                    ),
                    (
                        "test.bias".into(),
                        Tensor {
                            shape: vec![output],
                            values: vec![0.125; output],
                        },
                    ),
                ]),
            };
            let data = encode(&weights);
            let decoded = decode_with_storage(&data, false).unwrap();
            let mut packed = decode_with_storage(&data, true).unwrap();
            packed
                .packed_linears
                .get_mut("test.weight")
                .unwrap()
                .enable_fused();
            let reference = decoded.linear("test", input, output, true).unwrap();
            let fused = packed.linear("test", input, output, true).unwrap();
            assert!(fused.weight_by_input.is_empty());
            assert!(fused.packed_weight.as_ref().unwrap().bytes.is_empty());
            for rows in [0, 1, 3, 4, 5, 8, 17, 32] {
                let inputs = (0..rows * input)
                    .map(|i| {
                        if i % 11 == 0 {
                            0.0
                        } else {
                            (i as f32 * 0.1).cos()
                        }
                    })
                    .collect::<Vec<_>>();
                let mut expected = vec![0.0; rows * output];
                let mut actual = expected.clone();
                reference.apply_block_into(&inputs, &mut expected, rows);
                fused.apply_block_into(&inputs, &mut actual, rows);
                assert_eq!(
                    actual, expected,
                    "{rows} rows, {input} inputs, {output} outputs"
                );
                if rows > 0 {
                    assert_eq!(
                        fused.apply(&inputs[..input]),
                        reference.apply(&inputs[..input])
                    );
                }
            }
        }
    }

    #[test]
    fn rwkv_weight_precision_fused_preserves_complete_replay_states_and_bounds_query_error() {
        let path = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join(MODEL_PATH);
        let data = encode(&WeightMap::load(&path).unwrap());
        let mut decoded =
            runtime_with_compute(&path, Some(&data), StatePrecision::Int8, false, false);
        let mut fused = runtime_with_compute(&path, Some(&data), StatePrecision::Int8, true, true);
        let reviews = super::super::precision_tests::reviews(49);
        let expected = decoded.warm_up_reviews(reviews.clone(), true).unwrap();
        let actual = fused.warm_up_reviews(reviews.clone(), true).unwrap();
        assert_eq!(
            super::super::precision_tests::snapshot_bytes(&decoded),
            super::super::precision_tests::snapshot_bytes(&fused)
        );
        for ((_, expected), (_, actual)) in expected.iter().zip(&actual) {
            assert!((expected - actual).abs() < 1e-5, "{expected} vs {actual}");
        }
        let features = decoded.features.features_for(&reviews[0]);
        let state = decoded.warm_up_states.state_ref(&reviews[0]);
        let items = [ReviewPredictionQueryRef {
            features: &features,
            state,
        }; 130];
        let expected = decoded
            .model
            .review_retrievability_query_refs_batched(&items)
            .unwrap();
        let actual = fused
            .model
            .review_retrievability_query_refs_batched(&items)
            .unwrap();
        for (expected, actual) in expected.iter().zip(actual) {
            assert!((expected - actual).abs() < 1e-5, "{expected} vs {actual}");
        }
    }
}
