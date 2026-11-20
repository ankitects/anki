// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Matched pre-answer curve/instant predictions, using the production replay.
use std::io::Write;

use super::state_scope_bench::Row;
use super::*;

#[test]
#[ignore]
fn rwkv_model_quality_case() {
    let source: serde_json::Value =
        serde_json::from_slice(&fs::read(std::env::var("ANKI_RWKV_MODEL_INPUT").unwrap()).unwrap())
            .unwrap();
    let rows: Vec<Row> = serde_json::from_value(source["rows"].clone()).unwrap();
    let output = PathBuf::from(std::env::var("ANKI_RWKV_MODEL_OUTPUT").unwrap());
    let weights = std::env::var_os("ANKI_RWKV_MODEL_WEIGHTS")
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from(env!("CARGO_MANIFEST_DIR")).join(MODEL_PATH));
    let precision = StatePrecision::parse(
        &std::env::var("ANKI_RWKV_MODEL_PRECISION").unwrap_or_else(|_| "int8".into()),
    )
    .unwrap();
    let mut inference =
        RwkvInference::load_with_precision(weights, 0.9, 36_500, precision).unwrap();
    inference.captured_curves = Some(Vec::new());
    let mut curve_output =
        std::io::BufWriter::new(fs::File::create(output.join("curve.f32")).unwrap());
    let mut instant_output =
        std::io::BufWriter::new(fs::File::create(output.join("instant.f32")).unwrap());
    let mut previous_curves = HashMap::new();
    for (chunk_index, chunk) in rows.chunks(16_384).enumerate() {
        let inputs = chunk
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
        let instant = inference.warm_up_reviews(inputs, true).unwrap();
        let curves = inference.captured_curves.as_mut().unwrap();
        assert_eq!(curves.len(), chunk.len());
        assert_eq!(instant.len(), chunk.len());
        for (index, (row, curve)) in chunk.iter().zip(curves.drain(..)).enumerate() {
            let p = previous_curves.get(&row.1).map_or(0.5, |previous| {
                predict_curve(
                    previous,
                    if row.10 >= 0 {
                        row.10 as f32
                    } else {
                        row.9.saturating_mul(SECONDS_PER_DAY) as f32
                    },
                )
            });
            assert!(p.is_finite() && (0.0..=1.0).contains(&p));
            curve_output.write_all(&p.to_le_bytes()).unwrap();
            previous_curves.insert(row.1, curve);
            assert_eq!(instant[index].0, index);
            instant_output
                .write_all(&instant[index].1.to_le_bytes())
                .unwrap();
        }
        println!(
            "Completed {} retained answers",
            chunk_index * 16_384 + chunk.len()
        );
    }
    curve_output.flush().unwrap();
    instant_output.flush().unwrap();
}
