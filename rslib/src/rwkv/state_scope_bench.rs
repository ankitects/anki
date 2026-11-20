// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Offline shared-state precision POC. Production scope selection is unchanged.

use std::collections::BTreeMap;
use std::env;
use std::time::Instant;

use serde_json::json;

use super::*;

pub(super) type Row = (
    i64,
    i64,
    Option<i64>,
    Option<i64>,
    Option<i64>,
    u8,
    i64,
    i64,
    i64,
    i64,
    i64,
    bool,
);

/// `shared` selects deck, preset and global, in that order. Only offline tests
/// can use this loader; desktop preferences still quantize card/note alone.
pub(super) fn load_for_scopes(
    weights: PathBuf,
    precision: StatePrecision,
    shared: [bool; 3],
) -> RwkvInference {
    let mut inference =
        RwkvInference::load_with_precision(weights, 0.9, 36_500, precision).unwrap();
    let model = Arc::get_mut(&mut inference.model).unwrap();
    for (module, selected) in [1, 3, 4].into_iter().zip(shared) {
        if selected {
            model.modules[module].precision = precision;
        }
    }
    inference
}

pub(super) fn metrics(
    rows: &[Row],
    predictions: &[(usize, f32)],
    baseline: &[f32],
) -> serde_json::Value {
    assert_eq!(predictions.len(), rows.len());
    assert_eq!(baseline.len(), rows.len());
    let mut seen = HashSet::new();
    let mut counts = HashMap::<i64, (usize, usize)>::new();
    let mut bins = BTreeMap::<(i32, i32, i32), (usize, f64, f64)>::new();
    let mut loss = 0.0;
    let mut changes = Vec::new();
    for (index, (row, &(prediction_index, prediction))) in rows.iter().zip(predictions).enumerate()
    {
        assert_eq!(prediction_index, index);
        assert!(prediction.is_finite() && (0.0..=1.0).contains(&prediction));
        let has_previous = !seen.insert(row.1);
        let (long_term, lapses) = counts.entry(row.1).or_default();
        if row.9 >= 1 {
            if has_previous && row.11 {
                let probability = f64::from(prediction).clamp(1e-6, 1.0 - 1e-6);
                let outcome = f64::from(u8::from(row.5 != 1));
                loss -= if outcome == 1.0 {
                    probability.ln()
                } else {
                    (1.0 - probability).ln()
                };
                // Same elapsed/review/lapse calibration bins as the temporal
                // probe, including this long-term review in the review count.
                let key = (
                    count_bin(row.9 as f64, 248.0, 3.62),
                    count_bin((*long_term + 2) as f64, 1.99, 1.89),
                    if *lapses == 0 {
                        0
                    } else {
                        count_bin(*lapses as f64, 1.65, 1.73)
                    },
                );
                let bin = bins.entry(key).or_default();
                bin.0 += 1;
                bin.1 += f64::from(prediction);
                bin.2 += outcome;
                changes.push(f64::from((prediction - baseline[index]).abs()));
            }
            *long_term += 1;
        }
        *lapses += usize::from(row.5 == 1);
    }
    assert!(!changes.is_empty(), "no interday outcomes selected");
    changes.sort_by(f64::total_cmp);
    let count = changes.len();
    let squared_error: f64 = bins
        .values()
        .map(|&(n, prediction, outcome)| (prediction - outcome).powi(2) / n as f64)
        .sum();
    json!({
        "reviews": count,
        "log_loss": loss / count as f64,
        "rmse_bins": (squared_error / count as f64).sqrt(),
        "mean_abs_prediction_change": changes.iter().sum::<f64>() / count as f64,
        "p99_abs_prediction_change": changes[(count - 1) * 99 / 100],
        "max_abs_prediction_change": changes[count - 1],
    })
}

fn count_bin(value: f64, multiplier: f64, base: f64) -> i32 {
    (multiplier * base.powf(value.log(base).floor())).round_ties_even() as i32
}

#[test]
fn rwkv_state_scope_metrics_score_only_selected_interday_answers() {
    let rows = [
        (1, 10, None, None, None, 1, 1000, 0, 0, 5, 432_000, true),
        (2, 10, None, None, None, 3, 1000, 2, 1, 1, 86_400, true),
        (3, 10, None, None, None, 1, 1000, 2, 2, 1, 86_400, true),
        (4, 10, None, None, None, 3, 1000, 2, 2, 0, 0, true),
        (5, 10, None, None, None, 3, 1000, 2, 3, 1, 86_400, false),
    ];
    let result = metrics(
        &rows,
        &[(0, 0.1), (1, 0.8), (2, 0.3), (3, 0.1), (4, 0.1)],
        &[0.9, 0.7, 0.4, 0.9, 0.9],
    );
    assert_eq!(result["reviews"], 2);
    assert!(
        (result["log_loss"].as_f64().unwrap() - (-0.8_f64.ln() - 0.7_f64.ln()) / 2.0).abs() < 1e-7
    );
    assert!((result["rmse_bins"].as_f64().unwrap() - 0.065_f64.sqrt()).abs() < 1e-7);
    assert!((result["max_abs_prediction_change"].as_f64().unwrap() - 0.1).abs() < 1e-7);
    assert_eq!(count_bin(1.0, 248.0, 3.62), 248);
    assert_eq!(count_bin(2.0, 248.0, 3.62), 248);
    assert_eq!(count_bin(4.0, 248.0, 3.62), 898);
}

pub(super) fn scope_sizes(inference: &RwkvInference) -> serde_json::Value {
    let mut scopes = serde_json::Map::new();
    for (name, states) in [
        ("card", &inference.warm_up_states.card as &dyn StateMap),
        ("note", &inference.warm_up_states.note),
        ("deck", &inference.warm_up_states.deck),
        ("preset", &inference.warm_up_states.preset),
    ] {
        scopes.insert(
            name.into(),
            json!({
                "states": states.count(),
                "serialized_bytes": states.ids().iter().filter_map(|id| states.serialized_len(id)).sum::<usize>(),
            }),
        );
    }
    scopes.insert("global".into(), json!({
        "states": usize::from(inference.warm_up_states.global.is_some()),
        "serialized_bytes": inference.warm_up_states.global.as_ref().map_or(0, serialized_module_state_len),
    }));
    scopes.into()
}

#[test]
#[ignore]
fn rwkv_state_scope_metrics() {
    let input = env::var("ANKI_RWKV_STATE_SCOPE_INPUT").expect("set ANKI_RWKV_STATE_SCOPE_INPUT");
    let output =
        env::var("ANKI_RWKV_STATE_SCOPE_OUTPUT").expect("set ANKI_RWKV_STATE_SCOPE_OUTPUT");
    assert_ne!(Path::new(&input), Path::new(&output));
    let input_bytes = fs::read(&input).unwrap();
    let mut source: serde_json::Value = serde_json::from_slice(&input_bytes).unwrap();
    let rows: Vec<Row> =
        serde_json::from_value(source.as_object_mut().unwrap().remove("rows").unwrap()).unwrap();
    assert!(rows.windows(2).all(|pair| pair[0].0 < pair[1].0));
    let reviews: Vec<_> = rows
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
        .collect();
    let weights = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join(MODEL_PATH);
    let mut baseline = Vec::new();
    let mut results = Vec::new();
    for (name, precision, shared) in [
        ("f32", StatePrecision::F32, [false; 3]),
        ("int8_card_note", StatePrecision::Int8, [false; 3]),
        ("int8_plus_deck", StatePrecision::Int8, [true, false, false]),
        (
            "int8_plus_preset",
            StatePrecision::Int8,
            [false, true, false],
        ),
        (
            "int8_plus_global",
            StatePrecision::Int8,
            [false, false, true],
        ),
        ("int8_all", StatePrecision::Int8, [true; 3]),
    ] {
        let mut inference = load_for_scopes(weights.clone(), precision, shared);
        let started = Instant::now();
        let predictions = inference.warm_up_reviews(reviews.clone(), true).unwrap();
        let replay_seconds = started.elapsed().as_secs_f64();
        if baseline.is_empty() {
            baseline = predictions.iter().map(|&(_, p)| p).collect();
        }
        let accuracy = metrics(&rows, &predictions, &baseline);
        let sizes = scope_sizes(&inference);
        let total: u64 = sizes
            .as_object()
            .unwrap()
            .values()
            .map(|scope| scope["serialized_bytes"].as_u64().unwrap())
            .sum();
        println!(
            "{name}: bytes={total} loss={} rmse={} seconds={replay_seconds:.2}",
            accuracy["log_loss"], accuracy["rmse_bins"]
        );
        results.push(json!({
            "mode": name, "accuracy": accuracy, "scopes": sizes,
            "serialized_state_bytes": total, "prediction_replay_seconds": replay_seconds,
        }));
        fs::write(
            &output,
            serde_json::to_vec_pretty(&json!({
                "input_blake3": blake3::hash(&input_bytes).to_hex().to_string(),
                "model_blake3": blake3::hash(&fs::read(&weights).unwrap()).to_hex().to_string(),
                "backup_sha256": source["backup_sha256"], "retained_reviews": rows.len(),
                "results": results,
            }))
            .unwrap(),
        )
        .unwrap();
    }
}
