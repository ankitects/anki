// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Test-only resident int4 projection storage. One active projection is decoded
//! for the unchanged f32 compute kernels, with no full f32 model kept resident.

use super::weight_precision_bench::GROUP_SIZE;

#[derive(Clone)]
pub(super) struct PackedLinear {
    pub input: usize,
    pub output: usize,
    /// Validated original scales/codes; never quantize decoded weights again.
    pub bytes: Vec<u8>,
    pub fused: Option<super::fused_packed::FusedLinear>,
}

impl PackedLinear {
    pub fn enable_fused(&mut self) {
        self.fused = Some(super::fused_packed::FusedLinear::from_packed(self));
        // The transformed layout replaces the original codes, rather than
        // retaining two packed copies or a decoded f32 projection.
        self.bytes = Vec::new();
    }

    pub fn decode_by_input(&self) -> Vec<f32> {
        assert!(
            self.fused.is_none(),
            "fused projections must use their packed kernel"
        );
        let mut values = vec![0.0; self.input * self.output];
        let mut offset = 0;
        for row in 0..self.output {
            for column in (0..self.input).step_by(GROUP_SIZE) {
                let scale = f32::from_le_bytes(self.bytes[offset..offset + 4].try_into().unwrap());
                offset += 4;
                let count = (self.input - column).min(GROUP_SIZE);
                for index in 0..count {
                    let nibble = (self.bytes[offset + index / 2] >> (4 * (index % 2))) & 15;
                    let code = ((nibble as i8) << 4) >> 4;
                    values[(column + index) * self.output + row] = code as f32 * scale;
                }
                offset += count.div_ceil(2);
            }
        }
        debug_assert_eq!(offset, self.bytes.len());
        values
    }
}

#[cfg(test)]
mod tests {
    use super::super::weight_precision_bench::decode_with_storage;
    use super::super::weight_precision_bench::runtime_with_storage;
    use super::super::*;

    #[test]
    fn rwkv_weight_precision_resident_projection_matches_decoded_scalar_block_and_gemm() {
        let path = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join(MODEL_PATH);
        let data = super::super::weight_precision_bench::encode(&WeightMap::load(&path).unwrap());
        let decoded = decode_with_storage(&data, false).unwrap();
        let packed = decode_with_storage(&data, true).unwrap();
        for (weight_name, projection) in &packed.packed_linears {
            let name = weight_name.strip_suffix(".weight").unwrap();
            let (input, output) = (projection.input, projection.output);
            let bias = decoded.tensors.contains_key(&format!("{name}.bias"));
            let reference = decoded.linear(name, input, output, bias).unwrap();
            let resident = packed.linear(name, input, output, bias).unwrap();
            assert!(resident.weight_by_input.is_empty());
            assert_eq!(resident.weight_values().as_ref(), reference.weight_by_input);
            let inputs = (0..32 * input)
                .map(|i| (i as f32 * 0.1).sin())
                .collect::<Vec<_>>();
            assert_eq!(
                reference.apply(&inputs[..input]),
                resident.apply(&inputs[..input])
            );
            let mut expected = vec![0.0; 32 * output];
            let mut actual = expected.clone();
            reference.apply_block_into(&inputs, &mut expected, 32);
            resident.apply_block_into(&inputs, &mut actual, 32);
            assert_eq!(actual, expected);
            reference.apply_batch(&inputs, 32, &mut expected);
            resident.apply_batch(&inputs, 32, &mut actual);
            assert_eq!(actual, expected);
        }
        let _ = runtime_with_storage(&path, Some(&data), StatePrecision::Int8, true);
    }

    #[test]
    fn rwkv_weight_precision_resident_replay_and_queries_match_decoded_model() {
        let path = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join(MODEL_PATH);
        let data = super::super::weight_precision_bench::encode(&WeightMap::load(&path).unwrap());
        let mut decoded = runtime_with_storage(&path, Some(&data), StatePrecision::Int8, false);
        let mut packed = runtime_with_storage(&path, Some(&data), StatePrecision::Int8, true);
        let reviews = super::super::precision_tests::reviews(49);
        assert_eq!(
            decoded.warm_up_reviews(reviews.clone(), true).unwrap(),
            packed.warm_up_reviews(reviews.clone(), true).unwrap()
        );
        assert_eq!(
            super::super::precision_tests::snapshot_bytes(&decoded),
            super::super::precision_tests::snapshot_bytes(&packed)
        );
        let features = decoded.features.features_for(&reviews[0]);
        let state = decoded.warm_up_states.state_ref(&reviews[0]);
        let items = [ReviewPredictionQueryRef {
            features: &features,
            state,
        }; 130];
        assert_eq!(
            decoded
                .model
                .review_retrievability_query_refs_batched(&items)
                .unwrap(),
            packed
                .model
                .review_retrievability_query_refs_batched(&items)
                .unwrap()
        );
    }
}
