// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Offline accuracy/storage POC. Packed weights decode to f32 before inference;
//! desktop weight loading and compute kernels are unchanged.

use std::env;
use std::time::Instant;

use serde_json::json;

use super::state_scope_bench::metrics;
use super::state_scope_bench::scope_sizes;
use super::state_scope_bench::Row;
use super::*;

const MAGIC: &[u8] = b"ARWKVINT4POC1";
pub(super) const GROUP_SIZE: usize = 32;

fn projection(name: &str, tensor: &Tensor) -> bool {
    name.ends_with(".weight") && tensor.shape.len() == 2
}

pub(super) fn encode(weights: &WeightMap) -> Vec<u8> {
    let mut out = Vec::new();
    out.extend_from_slice(MAGIC);
    write_u32(&mut out, weights.tensors.len() as u32);
    let mut tensors = weights.tensors.iter().collect::<Vec<_>>();
    tensors.sort_unstable_by_key(|(name, _)| *name);
    for (name, tensor) in tensors {
        out.extend_from_slice(&(name.len() as u16).to_le_bytes());
        out.extend_from_slice(name.as_bytes());
        out.push(tensor.shape.len() as u8);
        for &dimension in &tensor.shape {
            write_u32(&mut out, dimension as u32);
        }
        let compact = projection(name, tensor);
        out.push(u8::from(compact));
        if compact {
            // Group along each output row's input dimension before the native
            // loader transposes weights to its input-major compute layout.
            for row in tensor.values.chunks_exact(tensor.shape[1]) {
                for group in row.chunks(GROUP_SIZE) {
                    let maximum = group.iter().map(|v| v.abs()).fold(0.0_f32, f32::max);
                    let scale = if maximum == 0.0 {
                        1.0
                    } else {
                        (maximum / 7.0).max(f32::from_bits(1))
                    };
                    assert!(scale.is_finite() && group.iter().all(|v| v.is_finite()));
                    out.extend_from_slice(&scale.to_le_bytes());
                    for pair in group.chunks(2) {
                        let code = |v: f32| {
                            (v / scale).round_ties_even().clamp(-7.0, 7.0) as i8 as u8 & 15
                        };
                        out.push(code(pair[0]) | (pair.get(1).map_or(0, |&v| code(v)) << 4));
                    }
                }
            }
        } else {
            for value in &tensor.values {
                out.extend_from_slice(&value.to_le_bytes());
            }
        }
    }
    out
}

fn decode(data: &[u8]) -> io::Result<WeightMap> {
    decode_with_storage(data, false)
}

pub(super) fn decode_with_storage(data: &[u8], resident: bool) -> io::Result<WeightMap> {
    let mut cursor = Cursor::new(data);
    cursor.expect_magic(MAGIC)?;
    let count = cursor.u32()? as usize;
    let mut tensors = HashMap::new();
    let mut packed_linears = HashMap::new();
    for _ in 0..count {
        let name_len = cursor.u16()? as usize;
        let name = cursor.string(name_len)?;
        let rank = cursor.u8()? as usize;
        let mut shape = Vec::new();
        let mut len = 1_usize;
        for _ in 0..rank {
            let dim = cursor.u32()? as usize;
            if dim == 0 {
                return Err(invalid_weights());
            }
            len = len.checked_mul(dim).ok_or_else(invalid_weights)?;
            shape.push(dim);
        }
        let encoding = cursor.u8()?;
        let mut values = Vec::new();
        match encoding {
            0 => {
                for _ in 0..len {
                    values.push(cursor.f32()?);
                }
            }
            1 if rank == 2 && name.ends_with(".weight") => {
                let packed_start = cursor.offset;
                for _ in 0..shape[0] {
                    let mut remaining = shape[1];
                    while remaining > 0 {
                        let group_len = remaining.min(GROUP_SIZE);
                        let scale = cursor.f32()?;
                        if !scale.is_finite() || scale <= 0.0 {
                            return Err(invalid_weights());
                        }
                        let codes = cursor.bytes(group_len.div_ceil(2))?;
                        for index in 0..group_len {
                            let nibble = (codes[index / 2] >> (4 * (index % 2))) & 15;
                            if nibble == 8 {
                                return Err(invalid_weights());
                            }
                            let code = ((nibble as i8) << 4) >> 4;
                            values.push(code as f32 * scale);
                        }
                        if group_len % 2 != 0 && codes.last().unwrap() >> 4 != 0 {
                            return Err(invalid_weights());
                        }
                        remaining -= group_len;
                    }
                }
                if resident {
                    packed_linears.insert(
                        name.clone(),
                        super::packed_weights::PackedLinear {
                            input: shape[1],
                            output: shape[0],
                            bytes: data[packed_start..cursor.offset].to_vec(),
                            fused: None,
                        },
                    );
                }
            }
            _ => return Err(invalid_weights()),
        }
        if values.iter().any(|v| !v.is_finite()) || tensors.contains_key(&name) {
            return Err(invalid_weights());
        }
        tensors.insert(name, Tensor { shape, values });
    }
    cursor.expect_end()?;
    Ok(WeightMap {
        tensors,
        packed_linears,
    })
}

fn invalid_weights() -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, "invalid offline int4 weights")
}

fn runtime(weights_path: &Path, packed: Option<&[u8]>, precision: StatePrecision) -> RwkvInference {
    runtime_with_storage(weights_path, packed, precision, false)
}

pub(super) fn runtime_with_storage(
    weights_path: &Path,
    packed: Option<&[u8]>,
    precision: StatePrecision,
    resident: bool,
) -> RwkvInference {
    runtime_with_compute(weights_path, packed, precision, resident, false)
}

pub(super) fn runtime_with_compute(
    weights_path: &Path,
    packed: Option<&[u8]>,
    precision: StatePrecision,
    resident: bool,
    fused: bool,
) -> RwkvInference {
    if let Some(packed) = packed {
        let mut weights = decode_with_storage(packed, resident).unwrap();
        if fused {
            assert!(resident);
            for projection in weights.packed_linears.values_mut() {
                projection.enable_fused();
            }
        }
        let model = SrsModel::from_weights(weights).unwrap();
        RwkvInference::from_model(model, 0.9, 36_500, precision)
    } else {
        RwkvInference::load_with_precision(weights_path.to_path_buf(), 0.9, 36_500, precision)
            .unwrap()
    }
}

#[test]
fn rwkv_weight_precision_preserves_float_tensors_and_quantizes_signed_odd_rows() {
    let weights = WeightMap {
        packed_linears: HashMap::new(),
        tensors: HashMap::from([
            (
                "projection.weight".into(),
                Tensor {
                    shape: vec![2, 5],
                    values: vec![7.0, -7.0, 0.5, 1.5, -1.5, 0.0, 0.0, 0.0, 0.0, 0.0],
                },
            ),
            (
                "projection.bias".into(),
                Tensor {
                    shape: vec![2],
                    values: vec![0.123, -0.456],
                },
            ),
            (
                "time_mixer.rkvdag_lerp".into(),
                Tensor {
                    shape: vec![1, 2],
                    values: vec![0.123, -0.456],
                },
            ),
        ]),
    };
    let packed = encode(&weights);
    let decoded = decode(&packed).unwrap();
    assert_eq!(decoded.tensors["projection.weight"].shape, [2, 5]);
    assert_eq!(
        decoded.tensors["projection.weight"].values,
        [7.0, -7.0, 0.0, 2.0, -2.0, 0.0, 0.0, 0.0, 0.0, 0.0]
    );
    for name in ["projection.bias", "time_mixer.rkvdag_lerp"] {
        assert_eq!(decoded.tensors[name].values, weights.tensors[name].values);
    }
    assert_eq!(encode(&decoded), packed);
}

#[test]
fn rwkv_weight_precision_groups_do_not_cross_projection_rows() {
    let mut values = vec![7.0; GROUP_SIZE];
    values.push(70.0);
    values.extend(vec![700.0; GROUP_SIZE]);
    values.push(7.0);
    let weights = WeightMap {
        packed_linears: HashMap::new(),
        tensors: HashMap::from([(
            "test.weight".into(),
            Tensor {
                shape: vec![2, GROUP_SIZE + 1],
                values: values.clone(),
            },
        )]),
    };
    assert_eq!(
        decode(&encode(&weights)).unwrap().tensors["test.weight"].values,
        values
    );
}

#[test]
fn rwkv_weight_precision_rejects_truncated_scales_codes_and_padding() {
    let weights = WeightMap {
        packed_linears: HashMap::new(),
        tensors: HashMap::from([(
            "test.weight".into(),
            Tensor {
                shape: vec![1, 3],
                values: vec![1.0, 2.0, 7.0],
            },
        )]),
    };
    let packed = encode(&weights);
    for length in [0, MAGIC.len(), packed.len() - 1] {
        assert!(decode(&packed[..length]).is_err());
    }
    let scale_offset = packed.len() - 6;
    for scale in [0.0, f32::NAN, f32::INFINITY] {
        let mut corrupt = packed.clone();
        corrupt[scale_offset..scale_offset + 4].copy_from_slice(&scale.to_le_bytes());
        assert!(decode(&corrupt).is_err());
    }
    let mut corrupt = packed.clone();
    corrupt[scale_offset + 4] = 8;
    assert!(decode(&corrupt).is_err());
    let mut corrupt = packed.clone();
    *corrupt.last_mut().unwrap() |= 0x10;
    assert!(decode(&corrupt).is_err());
}

#[test]
fn rwkv_weight_precision_native_model_loads_packed_round_trip() {
    let path = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join(MODEL_PATH);
    let weights = WeightMap::load(&path).unwrap();
    let packed = encode(&weights);
    assert!(packed.len() < fs::metadata(&path).unwrap().len() as usize / 4);
    let mut inference = runtime(&path, Some(&packed), StatePrecision::Int8);
    let reviews = (0..40)
        .map(|index| ReviewInput {
            card_id: index % 4 + 1,
            note_id: Some(index % 2 + 1),
            deck_id: Some(1),
            preset_id: Some(1),
            is_query: false,
            ease: Some((index % 4 + 1) as u8),
            duration_millis: Some(1234),
            card_type: Some(if index < 4 { 0 } else { 2 }),
            day_offset: Some(index / 4),
            current_elapsed_days: Some(1),
            current_elapsed_seconds: Some(86_400),
            target_retentions: [None; 4],
            enforce_grade_order: true,
        })
        .collect::<Vec<_>>();
    let predictions = inference.warm_up_reviews(reviews.clone(), true).unwrap();
    assert_eq!(predictions.len(), reviews.len());
    assert!(predictions
        .iter()
        .all(|&(_, p)| p.is_finite() && (0.0..=1.0).contains(&p)));
    let query = reviews.last().unwrap();
    let state = inference.warm_up_state(query);
    assert!(state
        .card
        .as_ref()
        .unwrap()
        .starts_with(super::precision::MAGIC));
    assert!(state.deck.as_ref().unwrap().starts_with(b"ARWKVMODSTATE1"));
}

#[test]
#[ignore]
fn rwkv_weight_precision_metrics() {
    let input =
        env::var("ANKI_RWKV_WEIGHT_PRECISION_INPUT").expect("set ANKI_RWKV_WEIGHT_PRECISION_INPUT");
    let output = env::var("ANKI_RWKV_WEIGHT_PRECISION_OUTPUT")
        .expect("set ANKI_RWKV_WEIGHT_PRECISION_OUTPUT");
    let model_output =
        env::var("ANKI_RWKV_WEIGHT_PRECISION_MODEL").expect("set ANKI_RWKV_WEIGHT_PRECISION_MODEL");
    assert_ne!(input, output);
    assert_ne!(input, model_output);
    assert_ne!(output, model_output);
    let input_bytes = fs::read(&input).unwrap();
    let mut source: serde_json::Value = serde_json::from_slice(&input_bytes).unwrap();
    let rows: Vec<Row> =
        serde_json::from_value(source.as_object_mut().unwrap().remove("rows").unwrap()).unwrap();
    assert!(rows.windows(2).all(|pair| pair[0].0 < pair[1].0));
    let reviews = rows
        .iter()
        .map(|row| ReviewInput {
            card_id: row.1,
            note_id: row.2,
            deck_id: row.3,
            preset_id: row.4,
            is_query: false,
            ease: Some(row.5),
            duration_millis: Some(row.6),
            card_type: Some(row.7),
            day_offset: Some(row.8),
            current_elapsed_days: Some(row.9),
            current_elapsed_seconds: Some(row.10),
            target_retentions: [None; 4],
            enforce_grade_order: true,
        })
        .collect::<Vec<_>>();
    let weights_path = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join(MODEL_PATH);
    assert_ne!(Path::new(&model_output), weights_path);
    let weights = WeightMap::load(&weights_path).unwrap();
    let original_bytes = fs::metadata(&weights_path).unwrap().len();
    let compact_parameters: usize = weights
        .tensors
        .iter()
        .filter(|(name, tensor)| projection(name, tensor))
        .map(|(_, tensor)| tensor.values.len())
        .sum();
    let parameters: usize = weights
        .tensors
        .values()
        .map(|tensor| tensor.values.len())
        .sum();
    let packed = encode(&weights);
    fs::write(&model_output, &packed).unwrap();
    // Exercise the artifact actually written to disk, including its decoder.
    let packed = fs::read(&model_output).unwrap();
    let mut baseline = Vec::new();
    let mut current = Vec::new();
    let mut results = Vec::new();
    for (name, compact, precision) in [
        ("f32_weights_f32_state", false, StatePrecision::F32),
        ("f32_weights_int8_state", false, StatePrecision::Int8),
        ("int4_weights_f32_state", true, StatePrecision::F32),
        ("int4_weights_int8_state", true, StatePrecision::Int8),
    ] {
        let mut inference = runtime(
            &weights_path,
            compact.then_some(packed.as_slice()),
            precision,
        );
        let started = Instant::now();
        let predictions = inference.warm_up_reviews(reviews.clone(), true).unwrap();
        let replay_seconds = started.elapsed().as_secs_f64();
        if baseline.is_empty() {
            baseline = predictions.iter().map(|&(_, p)| p).collect();
        }
        if name == "f32_weights_int8_state" {
            current = predictions.iter().map(|&(_, p)| p).collect();
        }
        let accuracy = metrics(&rows, &predictions, &baseline);
        let vs_current = if current.is_empty() {
            serde_json::Value::Null
        } else {
            metrics(&rows, &predictions, &current)
        };
        let scopes = scope_sizes(&inference);
        let state_bytes: u64 = scopes
            .as_object()
            .unwrap()
            .values()
            .map(|scope| scope["serialized_bytes"].as_u64().unwrap())
            .sum();
        let weight_bytes = if compact {
            packed.len() as u64
        } else {
            original_bytes
        };
        println!("{name}: weights={weight_bytes} state={state_bytes} loss={} rmse={} seconds={replay_seconds:.2}",
            accuracy["log_loss"], accuracy["rmse_bins"]);
        results.push(json!({
            "mode": name, "accuracy_vs_f32": accuracy, "accuracy_vs_current_int8": vs_current,
            "scopes": scopes, "serialized_state_bytes": state_bytes, "model_file_bytes": weight_bytes,
            "combined_payload_bytes": state_bytes + weight_bytes, "prediction_replay_seconds": replay_seconds,
        }));
        fs::write(&output, serde_json::to_vec_pretty(&json!({
            "input_blake3": blake3::hash(&input_bytes).to_hex().to_string(),
            "original_model_blake3": blake3::hash(&fs::read(&weights_path).unwrap()).to_hex().to_string(),
            "packed_model_blake3": blake3::hash(&packed).to_hex().to_string(),
            "backup_sha256": source["backup_sha256"], "retained_reviews": rows.len(),
            "group_size": GROUP_SIZE, "quantized_parameters": compact_parameters,
            "total_parameters": parameters, "runtime_weights_dtype": "f32", "results": results,
        })).unwrap()).unwrap();
    }
}
