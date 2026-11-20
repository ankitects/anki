// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Offline accuracy experiment; no collection or runtime cache is modified.

use std::env;
use std::time::Instant;

use rand::rngs::StdRng;
use rand::seq::SliceRandom;
use rand::Rng;
use rand::SeedableRng;
use serde::Deserialize;
use serde_json::json;

use super::*;

#[derive(Clone, Deserialize)]
struct Row(
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

impl Row {
    fn input(&self) -> ReviewInput {
        ReviewInput {
            card_id: self.1,
            note_id: self.2,
            deck_id: self.3,
            preset_id: self.4,
            is_query: false,
            ease: Some(self.5),
            duration_millis: Some(self.6),
            card_type: Some(self.7),
            day_offset: Some(self.8),
            current_elapsed_days: Some(self.9),
            current_elapsed_seconds: Some(self.10),
            target_retentions: [None; 4],
            enforce_grade_order: true,
        }
    }
}

fn omission_mask(
    rows: &[Row],
    split: usize,
    whole_cards: bool,
    pct: usize,
    seed: u64,
) -> Vec<bool> {
    let mut omitted = vec![false; rows.len()];
    let target = split * pct / 100;
    let mut rng = StdRng::seed_from_u64(seed);
    if whole_cards {
        let mut counts = HashMap::<i64, usize>::new();
        for row in &rows[..split] {
            *counts.entry(row.1).or_default() += 1;
        }
        let mut cards = counts.keys().copied().collect::<Vec<_>>();
        cards.sort_unstable();
        cards.shuffle(&mut rng);
        let mut dropped = HashSet::new();
        let mut count: usize = 0;
        for card in cards {
            let next = count + counts[&card];
            if next.abs_diff(target) > count.abs_diff(target) {
                break;
            }
            dropped.insert(card);
            count = next;
        }
        for (omit, row) in omitted[..split].iter_mut().zip(rows) {
            *omit = dropped.contains(&row.1);
        }
    } else {
        let mut indices = (0..split).collect::<Vec<_>>();
        indices.shuffle(&mut rng);
        for index in indices.into_iter().take(target) {
            omitted[index] = true;
        }
    }
    omitted
}

/// Sum elapsed times across holes using absolute timestamps and scheduler days.
/// Preserve each card's original first-review policy (creation time or
/// missing).
fn retained_inputs(rows: &[Row], omitted: &[bool], split: usize) -> (Vec<ReviewInput>, usize) {
    assert_eq!(rows.len(), omitted.len());
    let mut origins = HashMap::new();
    let mut previous = HashMap::<i64, (i64, i64)>::new();
    let mut inputs = Vec::new();
    let mut retained_prefix = 0;
    for (index, (row, &omit)) in rows.iter().zip(omitted).enumerate() {
        let origin = *origins.entry(row.1).or_insert((
            (row.10 >= 0).then_some(row.0 - row.10 * 1000),
            (row.9 >= 0).then_some(row.8 - row.9),
        ));
        if omit {
            assert!(index < split, "evaluation outcomes must never be omitted");
            continue;
        }
        let mut input = row.input();
        let last = previous.insert(row.1, (row.0, row.8));
        input.current_elapsed_seconds = Some(
            last.map(|last| last.0)
                .or(origin.0)
                .map_or(-1, |last| ((row.0 - last) / 1000).max(0)),
        );
        input.current_elapsed_days = Some(
            last.map(|last| last.1)
                .or(origin.1)
                .map_or(-1, |last| (row.8 - last).max(0)),
        );
        inputs.push(input);
        retained_prefix += usize::from(index < split);
    }
    (inputs, retained_prefix)
}

fn losses(predictions: &[(usize, f32)], rows: &[Row]) -> Vec<f64> {
    assert_eq!(predictions.len(), rows.len());
    predictions
        .iter()
        .zip(rows)
        .enumerate()
        .map(|(expected_index, (&(index, p), row))| {
            assert_eq!(index, expected_index);
            assert!(p.is_finite());
            let p = f64::from(p).clamp(1e-7, 1.0 - 1e-7);
            -(if row.5 == 1 { (1.0 - p).ln() } else { p.ln() })
        })
        .collect()
}

fn interday_answer_ids(rows: &[Row]) -> HashSet<i64> {
    let mut seen = HashSet::new();
    rows.iter()
        .filter_map(|row| {
            let has_previous_answer = !seen.insert(row.1);
            (has_previous_answer && row.9 >= 1).then_some(row.0)
        })
        .collect()
}

fn metrics(rows: &[Row], losses: &[f64], baseline: &[f64], selected: &[bool]) -> serde_json::Value {
    metrics_with_resamples(rows, losses, baseline, selected, 400)
}

fn metrics_with_resamples(
    rows: &[Row],
    losses: &[f64],
    baseline: &[f64],
    selected: &[bool],
    samples: usize,
) -> serde_json::Value {
    let mut per_card = HashMap::<i64, (f64, usize)>::new();
    let mut loss_sum = 0.0;
    let mut baseline_sum = 0.0;
    let mut count = 0;
    for (index, row) in rows.iter().enumerate() {
        if !selected[index] {
            continue;
        }
        count += 1;
        loss_sum += losses[index];
        baseline_sum += baseline[index];
        let cluster = per_card.entry(row.1).or_default();
        cluster.0 += losses[index] - baseline[index];
        cluster.1 += 1;
    }
    if count == 0 {
        return json!({"reviews": 0});
    }
    // Paired card-cluster bootstrap: correlated answers from a card stay together.
    let mut clusters = per_card.into_iter().collect::<Vec<_>>();
    clusters.sort_unstable_by_key(|item| item.0);
    let mut rng = StdRng::seed_from_u64(9407);
    let mut deltas = Vec::new();
    for _ in 0..samples {
        let mut sum = 0.0;
        let mut n = 0;
        for _ in 0..clusters.len() {
            let (_, (delta, reviews)) = clusters[rng.random_range(0..clusters.len())];
            sum += delta;
            n += reviews;
        }
        deltas.push(sum / n as f64);
    }
    deltas.sort_by(f64::total_cmp);
    json!({
        "reviews": count,
        "cards": clusters.len(),
        "log_loss": loss_sum / count as f64,
        "baseline_log_loss": baseline_sum / count as f64,
        "delta_log_loss": (loss_sum - baseline_sum) / count as f64,
        "relative_increase_pct": 100.0 * (loss_sum - baseline_sum) / baseline_sum,
        "delta_95pct_card_bootstrap": [deltas[samples / 40], deltas[samples - samples / 40 - 1]],
        "bootstrap_resamples": samples,
    })
}

#[test]
#[ignore]
fn rwkv_history_ablation_metrics() {
    let input_path = env::var("ANKI_RWKV_ABLATION_INPUT").expect("set ANKI_RWKV_ABLATION_INPUT");
    let output_path = env::var("ANKI_RWKV_ABLATION_OUTPUT").expect("set ANKI_RWKV_ABLATION_OUTPUT");
    let seeds: u64 = env::var("ANKI_RWKV_ABLATION_SEEDS")
        .unwrap_or("3".into())
        .parse()
        .unwrap();
    assert!(seeds > 0);
    let mut source: serde_json::Value =
        serde_json::from_slice(&std::fs::read(input_path).unwrap()).unwrap();
    let rows: Vec<Row> =
        serde_json::from_value(source.as_object_mut().unwrap().remove("rows").unwrap()).unwrap();
    assert!(rows.len() >= 10);
    assert!(rows.windows(2).all(|pair| pair[0].0 <= pair[1].0));
    let split = rows.len() * 4 / 5;
    let eval = &rows[split..];
    let all = eval.iter().map(|row| row.11).collect::<Vec<_>>();
    let interday = eval
        .iter()
        .map(|row| row.11 && row.9 >= 1)
        .collect::<Vec<_>>();
    let mut seen = HashSet::new();
    let first = eval
        .iter()
        .map(|row| seen.insert(row.1) && row.11)
        .collect::<Vec<_>>();
    assert!(all.iter().any(|&target| target));
    let weights = Path::new(env!("CARGO_MANIFEST_DIR")).join(MODEL_PATH);
    // Fix random ID features across conditions to isolate omitted review content.
    // Preassignment uses identities only, never answers or learned state.
    let mut features = FeatureState::default();
    for row in &rows {
        features.assign_id_encodings(&row.input());
    }
    let mut baseline = Vec::new();
    let mut results = Vec::new();
    for (mode, pct, seed) in std::iter::once(("baseline", 0, 0)).chain(
        ["whole_cards", "random_holes"]
            .into_iter()
            .flat_map(|mode| {
                [25, 50, 75]
                    .into_iter()
                    .flat_map(move |pct| (0..seeds).map(move |seed| (mode, pct, seed)))
            }),
    ) {
        let started = Instant::now();
        let omitted = omission_mask(&rows, split, mode == "whole_cards", pct, 20261010 + seed);
        let (mut inputs, retained_prefix) = retained_inputs(&rows, &omitted, split);
        if pct == 0 {
            for (input, row) in inputs.iter().zip(&rows) {
                assert_eq!(input.current_elapsed_days, Some(row.9));
                assert_eq!(input.current_elapsed_seconds, Some(row.10));
            }
        }
        let eval_inputs = inputs.split_off(retained_prefix);
        let mut inference = RwkvInference::load(weights.clone(), 0.9, 36_500).unwrap();
        inference.features = features.clone();
        inference.warm_up_reviews(inputs, false).unwrap();
        let predictions = inference.warm_up_reviews(eval_inputs, true).unwrap();
        let loss = losses(&predictions, eval);
        if pct == 0 {
            baseline = loss.clone();
        }
        let retained_cards = rows[..split]
            .iter()
            .zip(&omitted)
            .filter_map(|(row, &omit)| (!omit).then_some(row.1))
            .collect::<HashSet<_>>();
        let first_seen = eval
            .iter()
            .zip(&first)
            .map(|(row, &first)| first && retained_cards.contains(&row.1))
            .collect::<Vec<_>>();
        let first_unseen = eval
            .iter()
            .zip(&first)
            .map(|(row, &first)| first && !retained_cards.contains(&row.1))
            .collect::<Vec<_>>();
        let result = json!({
            "mode": mode, "requested_omission_pct": pct, "seed": 20261010 + seed,
            "omitted_prefix_reviews": split - retained_prefix,
            "actual_omission_pct": 100.0 * (split - retained_prefix) as f64 / split as f64,
            "retained_prefix_cards": retained_cards.len(),
            "all": metrics(eval, &loss, &baseline, &all),
            "interday": metrics(eval, &loss, &baseline, &interday),
            "first_per_card": metrics(eval, &loss, &baseline, &first),
            "first_seen_card": metrics(eval, &loss, &baseline, &first_seen),
            "first_unseen_card": metrics(eval, &loss, &baseline, &first_unseen),
            "elapsed_seconds": started.elapsed().as_secs_f64(),
        });
        println!(
            "{mode} omit={pct}% seed={seed} all={} interday={} first={} seconds={:.1}",
            result["all"]["log_loss"],
            result["interday"]["log_loss"],
            result["first_per_card"]["log_loss"],
            started.elapsed().as_secs_f64()
        );
        results.push(result);
        std::fs::write(
            &output_path,
            serde_json::to_vec_pretty(&json!({
                "source": source, "model": weights, "prefix_reviews": split,
                "evaluation_reviews": eval.len(), "evaluation_start_review_id": eval[0].0,
                "fixed_id_encodings": true, "results": results,
            }))
            .unwrap(),
        )
        .unwrap();
    }
}

#[test]
#[ignore]
fn rwkv_reset_history_metrics() {
    let input_path = env::var("ANKI_RWKV_ABLATION_INPUT").expect("set ANKI_RWKV_ABLATION_INPUT");
    let output_path = env::var("ANKI_RWKV_ABLATION_OUTPUT").expect("set ANKI_RWKV_ABLATION_OUTPUT");
    let mut source: serde_json::Value =
        serde_json::from_slice(&std::fs::read(input_path).unwrap()).unwrap();
    let variants: HashMap<String, Vec<Row>> =
        serde_json::from_value(source.as_object_mut().unwrap().remove("variants").unwrap())
            .unwrap();
    let complete = &variants["all_reviews"];
    let fresh = &variants["after_reset_only"];
    let scored = fresh
        .iter()
        .filter(|row| row.11)
        .cloned()
        .collect::<Vec<_>>();
    assert!(!scored.is_empty());
    let evaluation_start = scored[0].0;
    let reset_cards = source["reset_review_ids"]
        .as_object()
        .unwrap()
        .keys()
        .map(|id| id.parse::<i64>().unwrap())
        .collect::<HashSet<_>>();
    let rest = fresh
        .iter()
        .filter(|row| row.0 >= evaluation_start && !reset_cards.contains(&row.1))
        .cloned()
        .collect::<Vec<_>>();
    let expected = scored
        .iter()
        .map(|row| (row.0, row.1, row.5))
        .collect::<Vec<_>>();
    for rows in variants.values() {
        assert!(rows.windows(2).all(|pair| pair[0].0 < pair[1].0));
        assert_eq!(
            rows.iter()
                .filter(|row| row.11)
                .map(|row| (row.0, row.1, row.5))
                .collect::<Vec<_>>(),
            expected,
            "both conditions must score exactly the same outcomes"
        );
        assert_eq!(
            rows.iter()
                .filter(|row| row.0 >= evaluation_start && !reset_cards.contains(&row.1))
                .map(|row| (row.0, row.1, row.5))
                .collect::<Vec<_>>(),
            rest.iter()
                .map(|row| (row.0, row.1, row.5))
                .collect::<Vec<_>>(),
            "non-reset outcomes must also be identical"
        );
    }
    let all = vec![true; scored.len()];
    let mut seen = HashSet::new();
    let first = scored
        .iter()
        .map(|row| seen.insert(row.1))
        .collect::<Vec<_>>();
    let later = first.iter().map(|&is_first| !is_first).collect::<Vec<_>>();
    // Membership comes from the reset condition for both policies. Creation
    // elapsed time on a first query is not a gap since a previous answer.
    let interday_ids = interday_answer_ids(fresh);
    let interday = scored
        .iter()
        .map(|row| interday_ids.contains(&row.0))
        .collect::<Vec<_>>();
    let rest_all = vec![true; rest.len()];
    let rest_interday = rest
        .iter()
        .map(|row| interday_ids.contains(&row.0))
        .collect::<Vec<_>>();
    let weights = Path::new(env!("CARGO_MANIFEST_DIR")).join(MODEL_PATH);
    let mut features = FeatureState::default();
    for rows in [complete, fresh] {
        for row in rows {
            features.assign_id_encodings(&row.input());
        }
    }
    assert_eq!(features.review_index, 0);
    assert!(features.first_day_offset.is_none());
    let mut results = Vec::new();
    for (precision, precision_name) in
        [(StatePrecision::F32, "f32"), (StatePrecision::Int8, "int8")]
    {
        let mut baseline = Vec::new();
        let mut rest_baseline = Vec::new();
        for mode in ["all_reviews", "after_reset_only"] {
            let started = Instant::now();
            let rows = &variants[mode];
            let split = rows.iter().position(|row| row.11).unwrap();
            let mut inference =
                RwkvInference::load_with_precision(weights.clone(), 0.9, 36_500, precision)
                    .unwrap();
            inference.features = features.clone();
            inference
                .warm_up_reviews(rows[..split].iter().map(Row::input).collect(), false)
                .unwrap();
            let predictions = inference
                .warm_up_reviews(rows[split..].iter().map(Row::input).collect(), true)
                .unwrap();
            let loss = losses(&predictions, &rows[split..]);
            let selected_losses = rows[split..]
                .iter()
                .zip(&loss)
                .filter_map(|(row, &loss)| row.11.then_some(loss))
                .collect::<Vec<_>>();
            let rest_losses = rows[split..]
                .iter()
                .zip(&loss)
                .filter_map(|(row, &loss)| (!reset_cards.contains(&row.1)).then_some(loss))
                .collect::<Vec<_>>();
            if mode == "all_reviews" {
                baseline = selected_losses.clone();
                rest_baseline = rest_losses.clone();
            }
            let selected_predictions = predictions
                .iter()
                .zip(&rows[split..])
                .zip(&loss)
                .filter_map(|((&(_, p), row), &loss)| {
                    row.11.then_some((row.0, row.1, row.5, p, loss))
                })
                .collect::<Vec<_>>();
            let rest_predictions = predictions
                .iter()
                .zip(&rows[split..])
                .zip(&loss)
                .filter_map(|((&(_, p), row), &loss)| {
                    (!reset_cards.contains(&row.1)).then_some((row.0, row.1, row.5, p, loss))
                })
                .collect::<Vec<_>>();
            let result = json!({
                "mode": mode, "precision": precision_name,
                "replayed_answers": rows.len(),
                "omitted_answers": complete.len() - rows.len(),
                "all": metrics_with_resamples(&scored, &selected_losses, &baseline, &all, 10_000),
                "interday": metrics_with_resamples(&scored, &selected_losses, &baseline, &interday, 10_000),
                "first_per_card": metrics_with_resamples(&scored, &selected_losses, &baseline, &first, 10_000),
                "after_first": metrics_with_resamples(&scored, &selected_losses, &baseline, &later, 10_000),
                "rest_all": metrics(&rest, &rest_losses, &rest_baseline, &rest_all),
                "rest_interday": metrics(&rest, &rest_losses, &rest_baseline, &rest_interday),
                "predictions": selected_predictions,
                "rest_predictions": rest_predictions,
                "elapsed_seconds": started.elapsed().as_secs_f64(),
            });
            println!(
                "{mode} precision={} all={} later={} first={} rest={} seconds={:.1}",
                precision_name,
                result["all"]["log_loss"],
                result["after_first"]["log_loss"],
                result["first_per_card"]["log_loss"],
                result["rest_all"]["log_loss"],
                started.elapsed().as_secs_f64()
            );
            results.push(result);
            std::fs::write(
                &output_path,
                serde_json::to_vec_pretty(&json!({
                    "source": source, "model": weights, "fixed_id_encodings": true,
                    "evaluation_start_review_id": evaluation_start,
                    "results": results,
                }))
                .unwrap(),
            )
            .unwrap();
        }
    }
}

#[test]
fn reset_history_interday_requires_a_previous_retained_answer() {
    let rows = vec![
        Row(1, 1, None, None, None, 3, 0, 0, 100, 100, 100, true),
        Row(2, 2, None, None, None, 3, 0, 2, 100, 100, 100, true),
        Row(3, 1, None, None, None, 3, 0, 1, 100, 0, 1, true),
        Row(4, 1, None, None, None, 3, 0, 2, 101, 1, 86_400, true),
        Row(5, 2, None, None, None, 3, 0, 2, 101, 1, 86_400, true),
    ];
    assert_eq!(interday_answer_ids(&rows), HashSet::from([4, 5]));
}

#[test]
fn history_ablation_preserves_evaluation_and_whole_card_histories() {
    let rows = (0..100)
        .map(|i| Row(i * 1000, i % 10, None, None, None, 3, 0, 2, i, 1, 1, true))
        .collect::<Vec<_>>();
    for pct in [0, 25, 50, 75, 100] {
        for whole_cards in [false, true] {
            let mask = omission_mask(&rows, 80, whole_cards, pct, 1);
            assert!(mask[80..].iter().all(|&omit| !omit));
            let dropped = mask[..80].iter().filter(|&&omit| omit).count();
            if whole_cards {
                assert!(dropped.abs_diff(80 * pct / 100) <= 4);
            } else {
                assert_eq!(dropped, 80 * pct / 100);
            }
            if whole_cards {
                for card in 0..10 {
                    let decisions = rows[..80]
                        .iter()
                        .zip(&mask)
                        .filter_map(|(row, &omit)| (row.1 == card).then_some(omit))
                        .collect::<HashSet<_>>();
                    assert_eq!(decisions.len(), 1);
                }
            }
        }
    }
}

#[test]
fn history_ablation_elapsed_spans_missing_answers_and_keeps_first_review_policy() {
    let rows = vec![
        Row(100_000, 1, None, None, None, 3, 0, 0, 10, 2, 10, true),
        Row(120_000, 1, None, None, None, 3, 0, 1, 11, 1, 20, true),
        Row(150_000, 1, None, None, None, 3, 0, 2, 13, 2, 30, true),
    ];
    let (inputs, prefix) = retained_inputs(&rows, &[false, true, false], 2);
    assert_eq!(prefix, 1);
    assert_eq!(inputs[1].current_elapsed_seconds, Some(50));
    assert_eq!(inputs[1].current_elapsed_days, Some(3));
    let (inputs, prefix) = retained_inputs(&rows, &[true, true, false], 2);
    assert_eq!(prefix, 0);
    assert_eq!(inputs[0].current_elapsed_seconds, Some(60));
    assert_eq!(inputs[0].current_elapsed_days, Some(5));
    let mut rows = rows;
    rows[0].9 = -1;
    rows[0].10 = -1;
    let (inputs, _) = retained_inputs(&rows, &[true, true, false], 2);
    assert_eq!(inputs[0].current_elapsed_seconds, Some(-1));
    assert_eq!(inputs[0].current_elapsed_days, Some(-1));
}
