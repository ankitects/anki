// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! The bundled big model (the Big size, profile key `legacy`): the show-time
//! big model (SM91s2) since 2026-11-11 (before: the big model (1 leak-free
//! epoch)), checked on the big engine against vectors from the training code,
//! and the Big choice's RWKV-Instant model, the show-time instant-only big
//! model (SM99), checked the same way. Compiled into whichever engine module
//! runs the big architecture.

use std::io::Read;

use super::*;
use crate::rwkv::ModelKind;

#[derive(serde::Deserialize)]
struct VectorRow {
    features: Vec<f32>,
    ids: [i64; 5],
    skip: bool,
    elapsed: f32,
    /// The row's own interval inputs (elapsed seconds, elapsed days).
    interval: [f64; 2],
    fp32: Vec<f32>,
}

#[derive(serde::Deserialize)]
struct Vectors {
    name: String,
    sweep: Vec<f32>,
    rows: Vec<VectorRow>,
}

/// PyTorch outputs of the show-time big model (SM91s2) on two users' full
/// histories (show-time inputs, the inputs it was trained on and the ones
/// RWKV-Instant gives it; deleted cards: neighbour), first 500 rows per user;
/// `ANKI_RWKV_LEGACY_VECTORS` points the test at full vectors (show-time or
/// normal inputs).
fn legacy_vectors() -> Vec<Vectors> {
    read_vectors(
        "ANKI_RWKV_LEGACY_VECTORS",
        include_bytes!("testdata/legacy-vectors.json.gz"),
    )
}

/// PyTorch outputs of the show-time instant-only big model (SM99), made the
/// same way (show-time inputs, first 500 rows per user);
/// `ANKI_RWKV_SM99_VECTORS` points the test at full vectors.
fn sm99_vectors() -> Vec<Vectors> {
    read_vectors(
        "ANKI_RWKV_SM99_VECTORS",
        include_bytes!("testdata/sm99-vectors.json.gz"),
    )
}

fn read_vectors(variable: &str, bundled: &[u8]) -> Vec<Vectors> {
    let data = if let Ok(path) = std::env::var(variable) {
        fs::read(path).unwrap()
    } else {
        let mut data = Vec::new();
        flate2::read::GzDecoder::new(bundled)
            .read_to_end(&mut data)
            .unwrap();
        data
    };
    serde_json::from_slice(&data).unwrap()
}

fn big_model(file: &str, kind: ModelKind) -> SrsModel {
    let mut model = SrsModel::load(
        &PathBuf::from(env!("CARGO_MANIFEST_DIR"))
            .join("../qt/aqt/rwkv_inference")
            .join(file),
    )
    .unwrap();
    model.leak_free_inputs = kind.leak_free_inputs();
    model
}

fn legacy_model() -> SrsModel {
    big_model("RWKV_trained_on_5000_10000.bin", ModelKind::Legacy)
}

fn sm99_model() -> SrsModel {
    big_model("sm99/weights.f32.bin", ModelKind::Sm99)
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

/// Instant recall, the four button probabilities, curve recall at the row's
/// elapsed time and at the sweep times match the training code. The curve is
/// the trainer's raw forgetting curve: the runtime ignores the big model's
/// "ahead" logit residual (`predict_curve`).
#[test]
fn rwkv_legacy_matches_published_vectors() {
    let (rows, maximum, _) = check_published_vectors(&legacy_model(), legacy_vectors());
    println!("legacy published vectors: {rows} rows, maximum absolute probability error {maximum}");
}

/// The show-time instant-only big model (SM99) on the big engine: instant
/// recall and the four button probabilities (the outputs it serves) and the
/// untrained curve (never served, checked as engine parity only).
#[test]
fn rwkv_sm99_matches_published_vectors() {
    assert!(ModelKind::Sm99.instant_only());
    let (rows, maximum, instant) = check_published_vectors(&sm99_model(), sm99_vectors());
    println!(
        "sm99 published vectors: {rows} rows, maximum absolute probability error {maximum} (instant recall and buttons {instant})"
    );
}

/// `(rows, maximum error over every output, maximum over instant recall and
/// the four button probabilities)`.
fn check_published_vectors(model: &SrsModel, vectors: Vec<Vectors>) -> (usize, f32, f32) {
    let mut maximum = 0.0_f32;
    let mut instant = 0.0_f32;
    let mut rows = 0;
    for user in vectors {
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
            assert_eq!(actual.len(), row.fp32.len());
            for (head, (&a, &b)) in actual.iter().zip(&row.fp32).enumerate() {
                maximum = maximum.max((a - b).abs());
                if head < 5 {
                    instant = instant.max((a - b).abs());
                }
                assert!(
                    (a - b).abs() <= 1e-5,
                    "{} row {index} head {head}: {a} != {b}",
                    user.name
                );
            }
            if !row.skip {
                states.store(&input, heads.next_state);
            }
            rows += 1;
        }
    }
    (rows, maximum, instant)
}

/// The show-time big model (SM91s2), like the big model (1 leak-free epoch) it
/// was trained from, was trained with the missing note, deck and preset flags
/// always zero: the runtime feeds it that rule, so setting
/// them changes no output.
#[test]
fn rwkv_legacy_ignores_missing_id_flags() {
    assert!(ModelKind::Legacy.leak_free_inputs());
    check_ignores_missing_id_flags(&legacy_model(), legacy_vectors().remove(0));
}

/// The show-time instant-only big model (SM99) has the same input rule.
#[test]
fn rwkv_sm99_ignores_missing_id_flags() {
    assert!(ModelKind::Sm99.leak_free_inputs());
    check_ignores_missing_id_flags(&sm99_model(), sm99_vectors().remove(0));
}

fn check_ignores_missing_id_flags(model: &SrsModel, user: Vectors) {
    let mut states = ReviewStateMaps::default();
    for row in user.rows.iter().take(200) {
        assert!(row.features[13..16].iter().all(|&flag| flag == 0.0));
        let mut flagged = row.features.clone();
        flagged[13..16].fill(1.0);
        let input = vector_input(row);
        let state = states.state_ref(&input);
        let heads = model.review(&row.features, state);
        let flagged_heads = model.review(&flagged, state);
        assert_eq!(heads.retrievability, flagged_heads.retrievability);
        assert_eq!(
            heads.button_probabilities,
            flagged_heads.button_probabilities
        );
        assert_eq!(
            predict_curve(&heads.curve, row.elapsed),
            predict_curve(&flagged_heads.curve, row.elapsed)
        );
        if !row.skip {
            states.store(&input, heads.next_state);
        }
    }
}

/// The show-time big model (SM91s2) was trained on show-time intervals: a
/// query row's interval runs from the card's previous answer to the moment the
/// card is shown, and its cumulative interval is the card's committed
/// answer-to-answer intervals plus that interval; a committed answer keeps
/// the answer-to-answer interval. The native feature builder fed each row's
/// own interval reproduces the published interval inputs (features 0..8) of
/// every query and answer row.
#[test]
fn rwkv_legacy_feature_builder_matches_show_time_intervals() {
    let mut maximum = 0.0_f32;
    let mut queries = 0;
    for user in legacy_vectors() {
        let mut features = FeatureState::default();
        for (index, row) in user.rows.iter().enumerate() {
            let [seconds, days] = row.interval;
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
    println!("legacy interval features: {queries} queries, maximum absolute error {maximum}");
}
