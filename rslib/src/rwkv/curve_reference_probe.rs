// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Explicit, offline feature/curve export for an independent SM40 reference
//! replay.

use std::io::Write;

use serde_json::json;

use super::state_scope_bench::Row;
use super::*;

fn input(row: &Row) -> ReviewInput {
    ReviewInput {
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
        target_retentions: [Some(0.5); 4],
        enforce_grade_order: true,
    }
}

fn ids(input: &ReviewInput) -> [i64; 5] {
    [
        input.card_id,
        input.deck_id.unwrap(),
        input.note_id.unwrap(),
        input.preset_id.unwrap(),
        0,
    ]
}

fn head(heads: &ReviewHeads) -> serde_json::Value {
    let (scales, decays) = heads.curve.power.as_ref().unwrap();
    let sweep = [1.0, 3.0, 600.0, 86_400.0, 31_536_000.0, 3_153_600_000.0]
        .map(|seconds| predict_curve(&heads.curve, seconds));
    json!({
        "weights": heads.curve.weights,
        "scales_raw": scales,
        "decays_raw": decays,
        "instant": heads.retrievability,
        "ratings": heads.button_probabilities,
        "sweep": sweep,
    })
}

fn trace(inference: &mut RwkvInference, query: &ReviewInput) -> serde_json::Value {
    let mut features = vec![inference.features.features_for(query)];
    for ease in ANSWER_EASES {
        let answer = simulated_answer_input(query, ease);
        features.push(inference.features.features_for(&answer));
    }
    let heads: Vec<_> = features
        .iter()
        .map(|features| {
            inference
                .model
                .review(features, inference.warm_up_states.state_ref(query))
        })
        .collect();
    let mut crossings = serde_json::Map::new();
    for target in [0.9, 0.8, 0.75, 0.7, 0.6, 0.5] {
        let curves = std::array::from_fn(|i| &heads[i + 1].curve);
        crossings.insert(
            target.to_string(),
            json!({
                "raw": unrounded_intervals_for_answer_curves(curves, [target;4], 36_500, false),
                "pava": unrounded_intervals_for_answer_curves(curves, [target;4], 36_500, true),
            }),
        );
    }
    json!({"features": features, "heads": heads.iter().map(head).collect::<Vec<_>>(),
        "crossings": crossings})
}

fn restore(
    inference: &mut RwkvInference,
    original: &ReviewInput,
    features: &RwkvInferenceState,
    state: &ReviewStateOwned,
) {
    inference.restore_state(features);
    inference
        .restore_warm_up_state(
            original.card_id,
            original.note_id,
            original.deck_id,
            original.preset_id,
            ReviewStateOwned {
                card: state.card.clone(),
                note: state.note.clone(),
                deck: state.deck.clone(),
                preset: state.preset.clone(),
                global: state.global.clone(),
            },
        )
        .unwrap();
}

#[test]
#[ignore]
fn rwkv_sm40_curve_reference_trace() {
    let request: serde_json::Value = serde_json::from_slice(
        &fs::read(std::env::var("ANKI_RWKV_CURVE_PROBE_REQUEST").unwrap()).unwrap(),
    )
    .unwrap();
    let source: serde_json::Value =
        serde_json::from_slice(&fs::read(request["input"].as_str().unwrap()).unwrap()).unwrap();
    let rows: Vec<Row> = serde_json::from_value(source["rows"].clone()).unwrap();
    let anchors: Vec<usize> = serde_json::from_value(request["anchors"].clone()).unwrap();
    assert!(!anchors.is_empty() && anchors.windows(2).all(|pair| pair[0] < pair[1]));
    let output = PathBuf::from(request["output"].as_str().unwrap());
    fs::create_dir(&output).unwrap();
    let weights = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join(MODEL_PATH);
    let mut inference =
        RwkvInference::load_with_precision(weights, 0.9, 36_500, StatePrecision::F32).unwrap();
    let mut exported_features = FeatureState::default();
    let mut feature_file =
        std::io::BufWriter::new(fs::File::create(output.join("history.f32")).unwrap());
    let mut ids_file = std::io::BufWriter::new(fs::File::create(output.join("ids.i64")).unwrap());
    let mut cursor = 0;
    let mut traces = Vec::new();
    for &index in &anchors {
        let historical: Vec<_> = rows[cursor..index].iter().map(input).collect();
        for answered in &historical {
            for value in exported_features.features_for(answered) {
                feature_file.write_all(&value.to_le_bytes()).unwrap();
            }
            for id in ids(answered) {
                ids_file.write_all(&id.to_le_bytes()).unwrap();
            }
            exported_features.store_review(answered);
        }
        inference.warm_up_reviews(historical, false).unwrap();
        let mut first = Vec::new();
        let mut second = Vec::new();
        exported_features.write_cache_state(&mut first);
        inference.features.write_cache_state(&mut second);
        assert_eq!(
            first, second,
            "exported features differ from production replay"
        );
        cursor = index;
        let original = input(&rows[index]);
        let feature_snapshot = inference.state_for_review(
            original.card_id,
            original.note_id,
            original.deck_id,
            original.preset_id,
        );
        let state_snapshot = inference.warm_up_state(&original);
        let mut branches = Vec::new();
        for gap in [3, 30] {
            restore(
                &mut inference,
                &original,
                &feature_snapshot,
                &state_snapshot,
            );
            let mut query = original.clone();
            query.is_query = true;
            query.ease = None;
            query.duration_millis = None;
            let mut steps = Vec::new();
            for step in 0..=10 {
                if step > 0 {
                    query.card_type = Some(4);
                    query.current_elapsed_days = Some(0);
                    query.current_elapsed_seconds = Some(gap);
                }
                let mut sample = trace(&mut inference, &query);
                let mut answer = if step == 0 {
                    original.clone()
                } else {
                    simulated_answer_input(&query, 3)
                };
                if step > 0 {
                    answer.duration_millis = Some(1_000);
                }
                sample["actual_features"] = json!(inference.features.features_for(&answer));
                inference.warm_up_reviews(vec![answer], false).unwrap();
                steps.push(sample);
            }
            branches.push(json!({"gap_seconds":gap,"steps":steps}));
        }
        restore(
            &mut inference,
            &original,
            &feature_snapshot,
            &state_snapshot,
        );
        traces.push(json!({"history_index":index,"ids":ids(&original),"branches":branches}));
        println!("Captured anchor {} at history row {index}", traces.len());
    }
    feature_file.flush().unwrap();
    ids_file.flush().unwrap();
    fs::write(
        output.join("trace.json"),
        serde_json::to_vec(&json!({
            "history_rows":cursor,"feature_width":CARD_FEATURES,
            "source_input_blake3":blake3::hash(&fs::read(request["input"].as_str().unwrap()).unwrap()).to_hex().to_string(),
            "traces":traces,
        }))
        .unwrap(),
    )
    .unwrap();
}
