// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::io::Read;

use super::*;
use crate::rwkv::AnswerTimeHistory;
use crate::rwkv::ModelKind;
use crate::rwkv::ANSWER_TIME_BINS;

#[derive(serde::Deserialize)]
struct VectorRow {
    features: Vec<f32>,
    ids: [i64; 5],
    skip: bool,
    elapsed: f32,
    fp32: Vec<f32>,
    int8: Vec<f32>,
    /// The row's own interval inputs (elapsed seconds, elapsed days), when
    /// the package vectors were converted with them.
    #[serde(default)]
    interval: Option<[f64; 2]>,
}
#[derive(serde::Deserialize)]
struct Vectors {
    name: String,
    sweep: Vec<f32>,
    rows: Vec<VectorRow>,
}

#[test]
fn rwkv_sm40_matches_published_vectors() {
    let data = if let Ok(path) = std::env::var("ANKI_RWKV_SM40_VECTORS") {
        fs::read(path).unwrap()
    } else {
        let mut data = Vec::new();
        flate2::read::GzDecoder::new(&include_bytes!("testdata/sm40-vectors.json.gz")[..])
            .read_to_end(&mut data)
            .unwrap();
        data
    };
    check_published_vectors("sm40v2", &data);
}

#[test]
fn rwkv_sm36_matches_published_vectors() {
    let data = if let Ok(path) = std::env::var("ANKI_RWKV_SM36_VECTORS") {
        fs::read(path).unwrap()
    } else {
        let mut data = Vec::new();
        flate2::read::GzDecoder::new(&include_bytes!("testdata/sm36-vectors.json.gz")[..])
            .read_to_end(&mut data)
            .unwrap();
        data
    };
    check_published_vectors("sm36", &data);
}

#[test]
fn rwkv_sm55_matches_published_vectors() {
    let data = if let Ok(path) = std::env::var("ANKI_RWKV_SM55_VECTORS") {
        fs::read(path).unwrap()
    } else {
        let mut data = Vec::new();
        flate2::read::GzDecoder::new(&include_bytes!("testdata/sm55-vectors.json.gz")[..])
            .read_to_end(&mut data)
            .unwrap();
        data
    };
    check_published_vectors("sm55", &data);
}

#[test]
fn rwkv_sm56_matches_published_vectors() {
    let data = if let Ok(path) = std::env::var("ANKI_RWKV_SM56_VECTORS") {
        fs::read(path).unwrap()
    } else {
        let mut data = Vec::new();
        flate2::read::GzDecoder::new(&include_bytes!("testdata/sm56-vectors.json.gz")[..])
            .read_to_end(&mut data)
            .unwrap();
        data
    };
    check_published_vectors("sm56", &data);
}

fn sm72_vectors() -> Vec<u8> {
    if let Ok(path) = std::env::var("ANKI_RWKV_SM72_VECTORS") {
        fs::read(path).unwrap()
    } else {
        let mut data = Vec::new();
        flate2::read::GzDecoder::new(&include_bytes!("testdata/sm72-vectors.json.gz")[..])
            .read_to_end(&mut data)
            .unwrap();
        data
    }
}

fn sm72e20_vectors() -> Vec<u8> {
    if let Ok(path) = std::env::var("ANKI_RWKV_SM72E20_VECTORS") {
        fs::read(path).unwrap()
    } else {
        let mut data = Vec::new();
        flate2::read::GzDecoder::new(&include_bytes!("testdata/sm72e20-vectors.json.gz")[..])
            .read_to_end(&mut data)
            .unwrap();
        data
    }
}

fn sm72e25_vectors() -> Vec<u8> {
    if let Ok(path) = std::env::var("ANKI_RWKV_SM72E25_VECTORS") {
        fs::read(path).unwrap()
    } else {
        let mut data = Vec::new();
        flate2::read::GzDecoder::new(&include_bytes!("testdata/sm72e25-vectors.json.gz")[..])
            .read_to_end(&mut data)
            .unwrap();
        data
    }
}

fn sm92_vectors() -> Vec<u8> {
    if let Ok(path) = std::env::var("ANKI_RWKV_SM92_VECTORS") {
        fs::read(path).unwrap()
    } else {
        let mut data = Vec::new();
        flate2::read::GzDecoder::new(&include_bytes!("testdata/sm92-vectors.json.gz")[..])
            .read_to_end(&mut data)
            .unwrap();
        data
    }
}

/// The instant-only small model (SM57): same inputs as sm72e25 (normal
/// intervals). Its curve head is untrained but still part of the network the
/// vectors check.
fn sm57_vectors() -> Vec<u8> {
    if let Ok(path) = std::env::var("ANKI_RWKV_SM57_VECTORS") {
        fs::read(path).unwrap()
    } else {
        let mut data = Vec::new();
        flate2::read::GzDecoder::new(&include_bytes!("testdata/sm57-vectors.json.gz")[..])
            .read_to_end(&mut data)
            .unwrap();
        data
    }
}

/// The show-time instant-only model, stage 2 (SM98): show-time inputs, as
/// sm92 (the vectors carry each row's own interval). Its curve head is
/// untrained but still part of the network the vectors check.
fn sm98_vectors() -> Vec<u8> {
    if let Ok(path) = std::env::var("ANKI_RWKV_SM98_VECTORS") {
        fs::read(path).unwrap()
    } else {
        let mut data = Vec::new();
        flate2::read::GzDecoder::new(&include_bytes!("testdata/sm98-vectors.json.gz")[..])
            .read_to_end(&mut data)
            .unwrap();
        data
    }
}

/// The smaller instant-only model (SM101), the Tiny choice: SM98 with one
/// card layer, same show-time inputs (its vectors' inputs equal SM98's).
fn sm101_vectors() -> Vec<u8> {
    if let Ok(path) = std::env::var("ANKI_RWKV_SM101_VECTORS") {
        fs::read(path).unwrap()
    } else {
        let mut data = Vec::new();
        flate2::read::GzDecoder::new(&include_bytes!("testdata/sm101-vectors.json.gz")[..])
            .read_to_end(&mut data)
            .unwrap();
        data
    }
}

#[test]
fn rwkv_sm101_matches_published_vectors() {
    check_published_vectors("sm101", &sm101_vectors());
}

#[test]
fn rwkv_sm101_ignores_missing_id_flags_in_every_inference_path() {
    check_ignores_missing_id_flags("sm101", &sm101_vectors());
}

#[test]
fn rwkv_sm101_feature_builder_matches_show_time_intervals() {
    check_show_time_intervals("sm101", &sm101_vectors());
}

/// The Tiny two-head model (SM106), the Tiny choice's one model: the tiny
/// plan (one card layer) with a trained curve head, show-time inputs (its
/// vectors' inputs equal SM101's).
fn sm106_vectors() -> Vec<u8> {
    if let Ok(path) = std::env::var("ANKI_RWKV_SM106_VECTORS") {
        fs::read(path).unwrap()
    } else {
        let mut data = Vec::new();
        flate2::read::GzDecoder::new(&include_bytes!("testdata/sm106-vectors.json.gz")[..])
            .read_to_end(&mut data)
            .unwrap();
        data
    }
}

#[test]
fn rwkv_sm106_matches_published_vectors() {
    check_published_vectors("sm106", &sm106_vectors());
}

#[test]
fn rwkv_sm106_ignores_missing_id_flags_in_every_inference_path() {
    check_ignores_missing_id_flags("sm106", &sm106_vectors());
}

#[test]
fn rwkv_sm106_feature_builder_matches_show_time_intervals() {
    check_show_time_intervals("sm106", &sm106_vectors());
}

/// The small engine reads the card layers from the weights: one for SM101,
/// two for every other small model, at f32 and INT8 weights.
#[test]
fn rwkv_small_engine_reads_card_layers_from_the_weights() {
    for package in [
        "sm40v2", "sm36", "sm55", "sm56", "sm72", "sm72e20", "sm72e25", "sm92", "sm57", "sm98",
        "sm101", "sm106",
    ] {
        for format in ["f32", "int8"] {
            let model = SrsModel::load(&PathBuf::from(env!("CARGO_MANIFEST_DIR")).join(format!(
                "../qt/aqt/rwkv_inference/{package}/weights.{format}.bin"
            )))
            .unwrap();
            let plan = if matches!(package, "sm101" | "sm106") {
                TINY_LAYER_PLAN
            } else {
                ENGINE_LAYER_PLAN
            };
            assert_eq!(model.layer_plan, plan, "{package} {format}");
            let layers: Vec<_> = model.modules.iter().map(|m| m.layers.len()).collect();
            assert_eq!(layers, plan.module_layers, "{package} {format}");
        }
    }
}

#[test]
fn rwkv_sm98_matches_published_vectors() {
    check_published_vectors("sm98", &sm98_vectors());
}

#[test]
fn rwkv_sm98_ignores_missing_id_flags_in_every_inference_path() {
    check_ignores_missing_id_flags("sm98", &sm98_vectors());
}

#[test]
fn rwkv_sm57_matches_published_vectors() {
    check_published_vectors("sm57", &sm57_vectors());
}

#[test]
fn rwkv_sm57_ignores_missing_id_flags_in_every_inference_path() {
    check_ignores_missing_id_flags("sm57", &sm57_vectors());
}

#[test]
fn rwkv_sm72_matches_published_vectors() {
    check_published_vectors("sm72", &sm72_vectors());
}

#[test]
fn rwkv_sm72e20_matches_published_vectors() {
    check_published_vectors("sm72e20", &sm72e20_vectors());
}

#[test]
fn rwkv_sm72e25_matches_published_vectors() {
    check_published_vectors("sm72e25", &sm72e25_vectors());
}

#[test]
fn rwkv_sm92_matches_published_vectors() {
    check_published_vectors("sm92", &sm92_vectors());
}

/// The leak-free small models (SM72, every cool-down) were trained with the
/// missing note, deck and preset flags always zero. Setting them must not
/// change any of their outputs, while the same weights without the rule do
/// read them.
#[test]
fn rwkv_sm72_ignores_missing_id_flags_in_every_inference_path() {
    check_ignores_missing_id_flags("sm72", &sm72_vectors());
}

#[test]
fn rwkv_sm72e20_ignores_missing_id_flags_in_every_inference_path() {
    check_ignores_missing_id_flags("sm72e20", &sm72e20_vectors());
}

#[test]
fn rwkv_sm72e25_ignores_missing_id_flags_in_every_inference_path() {
    check_ignores_missing_id_flags("sm72e25", &sm72e25_vectors());
}

#[test]
fn rwkv_sm92_ignores_missing_id_flags_in_every_inference_path() {
    check_ignores_missing_id_flags("sm92", &sm92_vectors());
}

/// The show-time retrain of the release (SM92) was trained on show-time
/// intervals: a query row's interval runs from the card's previous answer to
/// the moment the card is shown, and its cumulative interval is the card's
/// committed answer-to-answer intervals plus that interval; a committed
/// answer keeps the answer-to-answer interval. The native feature builder
/// fed each row's own interval reproduces the published interval inputs
/// (features 0..8) of every query and answer row.
#[test]
fn rwkv_sm92_feature_builder_matches_show_time_intervals() {
    check_show_time_intervals("sm92", &sm92_vectors());
}

/// The show-time instant-only model, stage 2 (SM98) was trained on the same
/// show-time intervals as the show-time retrain (SM92).
#[test]
fn rwkv_sm98_feature_builder_matches_show_time_intervals() {
    check_show_time_intervals("sm98", &sm98_vectors());
}

fn check_show_time_intervals(package: &str, data: &[u8]) {
    let vectors: Vec<Vectors> = serde_json::from_slice(data).unwrap();
    let mut maximum = 0.0_f32;
    let mut queries = 0;
    for user in &vectors {
        let mut features = FeatureState::default();
        for (index, row) in user.rows.iter().enumerate() {
            let [seconds, days] = row
                .interval
                .expect("show-time vectors carry the row intervals");
            let mut input = vector_input(row);
            input.current_elapsed_seconds = Some(seconds as i64);
            input.current_elapsed_days = Some(days as i64);
            let actual = features.features_for(&input);
            for (feature, (&a, &b)) in actual[..8].iter().zip(&row.features[..8]).enumerate() {
                maximum = maximum.max((a - b).abs());
                assert!(
                    (a - b).abs() <= 1e-5,
                    "{} row {index} feature {feature}: {a} != {b}",
                    user.name
                );
            }
            if row.skip {
                queries += 1;
            } else {
                features.store_review(&input);
            }
        }
    }
    assert!(queries > 0);
    println!("{package} interval features: {queries} queries, maximum absolute error {maximum}");
}

fn check_ignores_missing_id_flags(package: &str, data: &[u8]) {
    let vectors: Vec<Vectors> = serde_json::from_slice(data).unwrap();
    let user = &vectors[0];
    let mut model = SrsModel::load(&PathBuf::from(env!("CARGO_MANIFEST_DIR")).join(format!(
        "../qt/aqt/rwkv_inference/{package}/weights.f32.bin"
    )))
    .unwrap();
    for leak_free in [false, true] {
        model.leak_free_inputs = leak_free;
        let mut states = ReviewStateMaps::default();
        let mut changed = false;
        for row in user.rows.iter().take(200) {
            assert!(row.features[13..16].iter().all(|&flag| flag == 0.0));
            let mut flagged = row.features.clone();
            flagged[13..16].fill(1.0);
            let input = vector_input(row);
            let state = states.state_ref(&input);
            let heads = model.review(&row.features, state);
            let flagged_heads = model.review(&flagged, state);
            let queries = [
                ReviewPredictionQueryRef {
                    features: &row.features,
                    state,
                },
                ReviewPredictionQueryRef {
                    features: &flagged,
                    state,
                },
            ];
            let scalar = model
                .review_retrievability_query_refs_scalar(&queries)
                .unwrap();
            let batched = model
                .review_retrievability_query_refs_batched(&queries)
                .unwrap();
            let same = heads.retrievability == flagged_heads.retrievability
                && heads.button_probabilities == flagged_heads.button_probabilities
                && predict_curve(&heads.curve, row.elapsed)
                    == predict_curve(&flagged_heads.curve, row.elapsed)
                && scalar[0] == scalar[1]
                && batched[0] == batched[1];
            if leak_free {
                assert!(same, "{} flags changed an output", user.name);
            }
            changed |= !same;
            if !row.skip {
                states.store(&input, heads.next_state);
            }
        }
        assert_eq!(changed, !leak_free);
    }
}

fn vector_input(row: &VectorRow) -> ReviewInput {
    ReviewInput {
        card_id: row.ids[0],
        deck_id: Some(row.ids[1]),
        note_id: Some(row.ids[2]),
        preset_id: Some(row.ids[3]),
        is_query: row.skip,
        ease: None,
        duration_millis: None,
        card_type: None,
        day_offset: None,
        current_elapsed_days: None,
        current_elapsed_seconds: None,
        target_retentions: [None; 4],
        enforce_grade_order: true,
    }
}

fn check_published_vectors(package: &str, data: &[u8]) {
    let vectors: Vec<Vectors> = serde_json::from_slice(data).unwrap();
    for format in ["f32", "int8"] {
        let mut model = SrsModel::load(&PathBuf::from(env!("CARGO_MANIFEST_DIR")).join(format!(
            "../qt/aqt/rwkv_inference/{package}/weights.{format}.bin"
        )))
        .unwrap();
        // Runtime input rule of the leak-free models; their vectors keep the
        // flags at zero, so this only mirrors the runtime configuration.
        model.leak_free_inputs = ModelKind::parse(package).is_ok_and(ModelKind::leak_free_inputs);
        let mut maximum = 0.0_f32;
        for user in &vectors {
            let mut states = ReviewStateMaps::default();
            for (index, row) in user.rows.iter().enumerate() {
                let input = vector_input(row);
                let heads = model.review(&row.features, states.state_ref(&input));
                let mut actual = vec![heads.retrievability.clamp(1e-5, 1.0 - 1e-5)];
                actual.extend(heads.button_probabilities);
                actual.push(predict_curve(&heads.curve, row.elapsed));
                actual.extend(
                    user.sweep
                        .iter()
                        .map(|&elapsed| predict_curve(&heads.curve, elapsed)),
                );
                let expected = if format == "f32" {
                    &row.fp32
                } else {
                    &row.int8
                };
                for (head, (&a, &b)) in actual.iter().zip(expected).enumerate() {
                    maximum = maximum.max((a - b).abs());
                    assert!(
                        (a - b).abs() <= 1e-5,
                        "{} {format} row {index} head {head}: {a} != {b}",
                        user.name
                    );
                }
                if !row.skip {
                    states.store(&input, heads.next_state);
                }
            }
        }
        println!(
            "{package} {format} published vectors: maximum absolute probability error {maximum}"
        );
    }
}

#[test]
fn rwkv_sm40_spec_matches_compiled_architecture() {
    for source in [
        include_str!("../../../qt/aqt/rwkv_inference/sm40v2/architecture.json"),
        include_str!("../../../qt/aqt/rwkv_inference/sm36/architecture.json"),
        include_str!("../../../qt/aqt/rwkv_inference/sm55/architecture.json"),
        include_str!("../../../qt/aqt/rwkv_inference/sm56/architecture.json"),
        include_str!("../../../qt/aqt/rwkv_inference/sm72/architecture.json"),
        include_str!("../../../qt/aqt/rwkv_inference/sm72e20/architecture.json"),
        include_str!("../../../qt/aqt/rwkv_inference/sm72e25/architecture.json"),
        include_str!("../../../qt/aqt/rwkv_inference/sm92/architecture.json"),
        include_str!("../../../qt/aqt/rwkv_inference/sm57/architecture.json"),
        include_str!("../../../qt/aqt/rwkv_inference/sm98/architecture.json"),
    ] {
        check_compiled_architecture(source, ENGINE_LAYER_PLAN);
    }
    for source in [
        include_str!("../../../qt/aqt/rwkv_inference/sm101/architecture.json"),
        include_str!("../../../qt/aqt/rwkv_inference/sm106/architecture.json"),
    ] {
        check_compiled_architecture(source, TINY_LAYER_PLAN);
    }
}

fn check_compiled_architecture(source: &str, plan: LayerPlan) {
    let spec: serde_json::Value = serde_json::from_str(source).unwrap();
    assert_eq!(spec["d_model"], D_MODEL);
    assert_eq!(spec["head_fc_dim"], HEAD_DIM);
    assert_eq!(spec["num_curves"], NUM_CURVES);
    for id in 0..5 {
        assert_eq!(spec["streams"][id]["n_layers"], plan.module_layers[id]);
        assert_eq!(spec["streams"][id]["head_size"], HEAD_SIZE);
        for layer in 0..plan.module_layers[id] {
            let stripped = spec["streams"][id]["channel_mixer_stripped_layers"]
                .as_array()
                .unwrap()
                .contains(&serde_json::json!(layer));
            assert_eq!(channel_mixer_present(id, layer), !stripped);
        }
    }
    let steps: Vec<(usize, usize)> = serde_json::from_value(spec["layer_steps"].clone()).unwrap();
    assert_eq!(steps, plan.steps);
}

#[derive(serde::Deserialize)]
struct AnswerTimeVectors {
    rows: Vec<usize>,
    review_th: Vec<usize>,
    explicit: Vec<Vec<f32>>,
    fp32: Vec<Vec<f32>>,
    int8: Vec<Vec<f32>>,
    /// card id, grade, duration ms, elapsed days of each review
    revlog: [Vec<i64>; 4],
}

#[derive(serde::Deserialize)]
struct AnswerTimeUser {
    name: String,
    rows: Vec<VectorRow>,
    time: AnswerTimeVectors,
}

/// The answer-time head vectors of the released small model (package
/// sm72e25-time). The bundled fixture keeps only the time part of the first
/// 500 rows; its rows are those of the model's own vectors.
fn sm72e25_answer_time_vectors() -> Vec<AnswerTimeUser> {
    answer_time_vectors(
        "ANKI_RWKV_SM72E25_TIME_VECTORS",
        include_bytes!("testdata/sm72e25-time-vectors.json.gz"),
        &sm72e25_vectors(),
    )
}

/// The answer-time head retrained on the show-time retrain of the release
/// (SM92), same layout as sm72e25's.
fn sm92_answer_time_vectors() -> Vec<AnswerTimeUser> {
    answer_time_vectors(
        "ANKI_RWKV_SM92_TIME_VECTORS",
        include_bytes!("testdata/sm92-time-vectors.json.gz"),
        &sm92_vectors(),
    )
}

fn answer_time_vectors(variable: &str, fixture: &[u8], vectors: &[u8]) -> Vec<AnswerTimeUser> {
    let data = if let Ok(path) = std::env::var(variable) {
        fs::read(path).unwrap()
    } else {
        let mut data = Vec::new();
        flate2::read::GzDecoder::new(fixture)
            .read_to_end(&mut data)
            .unwrap();
        data
    };
    let mut users: Vec<AnswerTimeUser> = serde_json::from_slice(&data).unwrap();
    let model: Vec<Vectors> = serde_json::from_slice(vectors).unwrap();
    for user in &mut users {
        if user.rows.is_empty() {
            let rows = &model
                .iter()
                .find(|other| other.name == user.name)
                .unwrap()
                .rows;
            user.rows = rows
                .iter()
                .map(|row| VectorRow {
                    features: row.features.clone(),
                    ids: row.ids,
                    skip: row.skip,
                    elapsed: row.elapsed,
                    fp32: row.fp32.clone(),
                    int8: row.int8.clone(),
                    interval: row.interval,
                })
                .collect();
        }
    }
    users
}

fn grade_softmax(logits: &[f32]) -> Vec<f64> {
    (0..logits.len() / ANSWER_TIME_BINS)
        .map(|grade| &logits[grade * ANSWER_TIME_BINS..(grade + 1) * ANSWER_TIME_BINS])
        .flat_map(|grade| {
            let max = grade.iter().copied().fold(f32::NEG_INFINITY, f32::max) as f64;
            let exp: Vec<f64> = grade.iter().map(|&v| (v as f64 - max).exp()).collect();
            let sum: f64 = exp.iter().sum();
            exp.into_iter().map(move |v| v / sum)
        })
        .collect()
}

/// The 26 history features of the answer-time head, recomputed from each
/// user's revlog, equal the Python reference's at every published query.
#[test]
fn rwkv_sm72e25_answer_time_history_matches_published_features() {
    check_answer_time_history("sm72e25", sm72e25_answer_time_vectors());
}

#[test]
fn rwkv_sm92_answer_time_history_matches_published_features() {
    check_answer_time_history("sm92", sm92_answer_time_vectors());
}

fn check_answer_time_history(package: &str, users: Vec<AnswerTimeUser>) {
    let mut maximum = 0.0_f32;
    for user in users {
        let [cards, grades, durations, elapsed] = &user.time.revlog;
        let mut wanted: HashMap<usize, usize> = HashMap::new();
        for (index, &review_th) in user.time.review_th.iter().enumerate() {
            wanted.insert(review_th, index);
        }
        let mut history = AnswerTimeHistory::new();
        let mut checked = 0;
        for review in 0..cards.len() {
            if let Some(&index) = wanted.get(&(review + 1)) {
                let actual = history.features(cards[review], elapsed[review]);
                for (feature, (&a, &b)) in actual.iter().zip(&user.time.explicit[index]).enumerate()
                {
                    maximum = maximum.max((a - b).abs());
                    assert!(
                        (a - b).abs() <= 1e-6,
                        "{} review {} feature {feature}: {a} != {b}",
                        user.name,
                        review + 1
                    );
                }
                checked += 1;
            }
            history.push(cards[review], grades[review], durations[review]);
        }
        assert_eq!(checked, user.time.rows.len(), "{}", user.name);
    }
    println!("{package} answer-time history features: maximum absolute error {maximum}");
}

/// The answer-time head on the native trunk reproduces the Python reference
/// (the frozen-feature path the head was trained and scored on) for both
/// weight formats, and its trunk pass gives the grade path's own button
/// probabilities bit for bit.
#[test]
fn rwkv_sm72e25_answer_time_head_matches_published_vectors() {
    check_answer_time_head("sm72e25", sm72e25_answer_time_vectors());
}

#[test]
fn rwkv_sm92_answer_time_head_matches_published_vectors() {
    check_answer_time_head("sm92", sm92_answer_time_vectors());
}

fn check_answer_time_head(package: &str, users: Vec<AnswerTimeUser>) {
    let folder = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join(format!("../qt/aqt/rwkv_inference/{package}"));
    for format in ["f32", "int8"] {
        let mut model = SrsModel::load(&folder.join(format!("weights.{format}.bin"))).unwrap();
        model.leak_free_inputs = true;
        let head = AnswerTimeHead::load(&folder.join(format!("time_head.{format}.bin"))).unwrap();
        let (mut maximum, mut logit_maximum, mut checked) = (0.0_f64, 0.0_f32, 0);
        for user in &users {
            let time_rows: HashMap<usize, usize> = user
                .time
                .rows
                .iter()
                .enumerate()
                .map(|(index, &row)| (row, index))
                .collect();
            let mut states = ReviewStateMaps::default();
            for (index, row) in user.rows.iter().enumerate() {
                let input = vector_input(row);
                let heads = model.review(&row.features, states.state_ref(&input));
                if let Some(&time) = time_rows.get(&index) {
                    let (frozen, buttons) =
                        model.answer_time_inputs(&row.features, states.state_ref(&input));
                    assert_eq!(
                        buttons, heads.button_probabilities,
                        "{} row {index}",
                        user.name
                    );
                    let logits = head.logits(&frozen, &user.time.explicit[time]);
                    let expected = if format == "f32" {
                        &user.time.fp32[time]
                    } else {
                        &user.time.int8[time]
                    };
                    for (&a, &b) in logits.iter().zip(expected) {
                        logit_maximum = logit_maximum.max((a - b).abs());
                    }
                    for (bin, (a, b)) in grade_softmax(&logits)
                        .into_iter()
                        .zip(grade_softmax(expected))
                        .enumerate()
                    {
                        maximum = maximum.max((a - b).abs());
                        assert!(
                            (a - b).abs() <= 1e-5,
                            "{} {format} row {index} bin {bin}: {a} != {b}",
                            user.name
                        );
                    }
                    checked += 1;
                }
                if !row.skip {
                    states.store(&input, heads.next_state);
                }
            }
        }
        assert!(checked > 0);
        println!(
            "{package} {format} answer-time head: {checked} queries, maximum absolute bin probability error {maximum}, logit error {logit_maximum}"
        );
    }
}
