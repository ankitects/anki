// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Offline experiment comparing review ORDERING strategies under a backlog.
//!
//! The day loop is a bench-local copy of `simulate_workload_for_target` with a
//! pluggable ordering and extra instrumentation. Ground truth is RWKV's own
//! predicted retrievability (the grade is drawn from it), exactly as in the
//! production simulator. Production behaviour is untouched.

use std::collections::BTreeMap;
use std::collections::VecDeque;
use std::env;
use std::time::Instant;

use serde_json::json;
use serde_json::Value;

use super::state_scope_bench::scope_sizes;
use super::state_scope_bench::Row;
use super::*;

const TARGET_RETENTION: f32 = 0.9;
const MAX_INTERVAL: u32 = 36_500;
const GRADE_SECONDS: [f32; 4] = [20.0, 15.0, 8.0, 6.0];
const MIX_SALT: i64 = -7;
const BIN_LABELS: [&str; 8] = ["0", "1", "2-3", "4-7", "8-14", "15-30", "31-90", ">90"];

#[derive(Clone, Copy)]
enum Kind {
    Reference(ReviewCardOrder),
    RandomDaily,
    Mix(u64),
    RoundRobinDeck,
    RoundRobinPreset,
}

#[derive(Clone)]
struct Strategy {
    name: String,
    kind: Kind,
    /// 0 reproduces the production grade draws; other values re-draw them so
    /// repeated runs of one ordering measure the simulation noise.
    grade_seed: i64,
}

fn strategies() -> Vec<Strategy> {
    let strategy = |name: &str, kind| Strategy {
        name: name.into(),
        kind,
        grade_seed: 0,
    };
    let noise = |name: &str, kind, grade_seed| Strategy {
        name: name.into(),
        kind,
        grade_seed,
    };
    vec![
        strategy(
            "asc_r",
            Kind::Reference(ReviewCardOrder::RetrievabilityAscending),
        ),
        strategy(
            "desc_r",
            Kind::Reference(ReviewCardOrder::RetrievabilityDescending),
        ),
        strategy(
            "relative_overdueness",
            Kind::Reference(ReviewCardOrder::RelativeOverdueness),
        ),
        strategy("random_daily", Kind::RandomDaily),
        strategy("random_static", Kind::Reference(ReviewCardOrder::Random)),
        strategy("mix_2", Kind::Mix(2)),
        strategy("mix_4", Kind::Mix(4)),
        strategy("mix_10", Kind::Mix(10)),
        strategy("rr_deck", Kind::RoundRobinDeck),
        strategy("rr_preset", Kind::RoundRobinPreset),
        // Same ordering as asc_r / random_daily with other grade draws.
        noise(
            "asc_r_noise1",
            Kind::Reference(ReviewCardOrder::RetrievabilityAscending),
            1,
        ),
        noise(
            "asc_r_noise2",
            Kind::Reference(ReviewCardOrder::RetrievabilityAscending),
            2,
        ),
        noise("random_daily_noise1", Kind::RandomDaily, 1),
    ]
}

#[derive(Clone)]
struct Scenario {
    name: &'static str,
    days: i64,
    review_limit: usize,
    /// Every card is aged by this many days before day 0.
    age_days: i64,
}

struct RunResult {
    metrics: Value,
    reviews_per_day: Vec<u32>,
    summary: BTreeMap<&'static str, f64>,
    /// Flat shock-experiment metrics; empty without a `ShockSpec`.
    shock_summary: BTreeMap<String, f64>,
}

/// Cards whose true R differs from the model's prediction.
struct ShockSpec {
    /// Measured card groups (name, membership by input index).
    groups: Vec<(&'static str, Vec<bool>)>,
    /// Group whose true R is the square of the predicted R.
    applied: Option<usize>,
}

fn masked_mean(values: &[f32], mask: &[bool], member: bool) -> f64 {
    let selected: Vec<f32> = values
        .iter()
        .zip(mask)
        .filter(|(_, &m)| m == member)
        .map(|(&v, _)| v)
        .collect();
    mean(&selected)
}

// ---------------------------------------------------------------------------
// Pure selection helpers
// ---------------------------------------------------------------------------

/// Take cards for `limit` slots; every `k`th slot comes from the random list,
/// the others from the ascending-R list. `counter` persists across days.
fn mix_select(
    asc: &[usize],
    random: &[usize],
    k: u64,
    limit: usize,
    counter: &mut u64,
    card_count: usize,
) -> Vec<usize> {
    let mut taken = vec![false; card_count];
    let (mut asc_pos, mut random_pos) = (0, 0);
    let mut selected = Vec::new();
    while selected.len() < limit.min(asc.len()) {
        let (list, pos) = if *counter % k == k - 1 {
            (random, &mut random_pos)
        } else {
            (asc, &mut asc_pos)
        };
        while taken[list[*pos]] {
            *pos += 1;
        }
        taken[list[*pos]] = true;
        selected.push(list[*pos]);
        *counter += 1;
    }
    selected
}

/// Round-robin across groups; `asc` is ascending R, so groups are ordered by
/// their lowest R and each group is taken in ascending R.
fn round_robin_select(
    asc: &[usize],
    group_of: impl Fn(usize) -> Option<i64>,
    limit: usize,
) -> Vec<usize> {
    let mut group_index = HashMap::<Option<i64>, usize>::new();
    let mut groups: Vec<VecDeque<usize>> = Vec::new();
    for &index in asc {
        let slot = *group_index.entry(group_of(index)).or_insert_with(|| {
            groups.push(VecDeque::new());
            groups.len() - 1
        });
        groups[slot].push_back(index);
    }
    let mut selected = Vec::new();
    while selected.len() < limit {
        let mut progressed = false;
        for group in &mut groups {
            if selected.len() >= limit {
                break;
            }
            if let Some(index) = group.pop_front() {
                selected.push(index);
                progressed = true;
            }
        }
        if !progressed {
            break;
        }
    }
    selected
}

fn quantile(sorted: &[f32], q: f64) -> f64 {
    if sorted.is_empty() {
        return f64::NAN;
    }
    f64::from(sorted[((sorted.len() - 1) as f64 * q).round() as usize])
}

fn mean_f64(values: &[f64]) -> f64 {
    if values.is_empty() {
        return f64::NAN;
    }
    values.iter().sum::<f64>() / values.len() as f64
}

fn sorted(values: &[f32]) -> Vec<f32> {
    let mut values = values.to_vec();
    values.sort_by(f32::total_cmp);
    values
}

fn mean(values: &[f32]) -> f64 {
    if values.is_empty() {
        return f64::NAN;
    }
    values.iter().map(|&v| f64::from(v)).sum::<f64>() / values.len() as f64
}

fn fraction(values: &[f32], predicate: impl Fn(f32) -> bool) -> f64 {
    if values.is_empty() {
        return f64::NAN;
    }
    values.iter().filter(|&&v| predicate(v)).count() as f64 / values.len() as f64
}

fn sanitize(r: f32) -> f32 {
    valid_probability_or_default(r, TARGET_RETENTION)
}

// ---------------------------------------------------------------------------
// Day loop
// ---------------------------------------------------------------------------

fn run_strategy(
    inference: &RwkvInference,
    inputs: &[RwkvWorkloadSimulationInput],
    scenario: &Scenario,
    strategy: &Strategy,
    counterfactual: bool,
    shock: Option<&ShockSpec>,
) -> io::Result<RunResult> {
    let started = Instant::now();
    let tr = TARGET_RETENTION;
    let review_model = RwkvWorkloadReviewModel {
        grade_seconds: GRADE_SECONDS,
        bucket_probabilities: Vec::new(),
    };
    let mut worker = inference.workload_worker(inference.features.clone(), HashMap::new());
    let mut states = SimulationStates::new(&inference.warm_up_states);

    // Age every card, then build the cards exactly as `simulation_card` does
    // from a query at that age.
    let aged: Vec<RwkvWorkloadSimulationInput> = inputs
        .iter()
        .cloned()
        .map(|mut input| {
            if let Some(elapsed) = input.review_input.current_elapsed_days.as_mut() {
                *elapsed += scenario.age_days;
            }
            input
        })
        .collect();
    let query_inputs: Vec<_> = aged
        .iter()
        .map(|input| simulation_query_input(input, tr, 0))
        .collect();
    let predictions = worker.workload_query_predictions_for_inputs(&query_inputs, &states);
    let mut cards: Vec<RwkvSimulationCard> = aged
        .iter()
        .zip(&predictions)
        .map(|(input, prediction)| simulation_card(input, prediction, tr))
        .collect();
    let n = cards.len();
    let days = scenario.days;
    let card_id =
        |cards: &[RwkvSimulationCard], index: usize| cards[index].review_input.review_input.card_id;

    let applied_mask: Vec<bool> = match shock {
        Some(spec) => spec
            .applied
            .map_or(vec![false; n], |group| spec.groups[group].1.clone()),
        None => vec![false; n],
    };
    let true_of = |index: usize, r: f32| {
        if applied_mask[index] {
            r * r
        } else {
            r
        }
    };
    let deck_of =
        |cards: &[RwkvSimulationCard], index: usize| cards[index].review_input.review_input.deck_id;
    let mut min_true_after_start = vec![1.0f32; n];
    let mut final_true: Vec<f32> = Vec::new();
    let mut first_review_day: Vec<Option<i64>> = vec![None; n];
    // (card index, predicted R, true R, lapse) per review.
    let mut review_log: Vec<(usize, f32, f32, bool)> = Vec::new();
    let group_count = shock.map_or(0, |spec| spec.groups.len());
    let mut group_pred_series: Vec<Vec<f64>> = vec![Vec::new(); group_count];
    let mut group_true_series: Vec<Vec<f64>> = vec![Vec::new(); group_count];
    let mut deck_last_day = HashMap::<Option<i64>, i64>::new();
    let mut deck_max_gap = HashMap::<Option<i64>, i64>::new();
    let mut deck_entropy: Vec<f64> = Vec::new();
    let mut deck_distinct: Vec<f64> = Vec::new();
    let deck_cards = {
        let mut counts = HashMap::<Option<i64>, usize>::new();
        for card in &cards {
            *counts
                .entry(card.review_input.review_input.deck_id)
                .or_default() += 1;
        }
        counts
    };

    let mut mix_counter = 0u64;
    let mut total_cost = 0.0f64;
    let mut review_rs: Vec<f32> = Vec::new();
    let mut intervals: Vec<f32> = Vec::new();
    let mut first_intervals: Vec<f32> = Vec::new();
    let mut lapses = 0u64;
    let mut ever_reviewed = vec![false; n];
    let mut reviews_per_day = Vec::new();
    let mut invalid_r = 0u64;

    let mut min_r = vec![1.0f32; n];
    let mut min_r_after_start = vec![1.0f32; n];
    let mut prev_r = vec![f32::NAN; n];
    let mut r_hat = vec![f32::NAN; n];
    let mut reviewed_prev = vec![false; n];
    let mut drops: Vec<f32> = Vec::new();
    let mut revisions: Vec<f32> = Vec::new();
    let mut max_drop_per_card = vec![0.0f32; n];
    let mut min_revision_per_card = vec![0.0f32; n];

    let mut series = BTreeMap::<&str, Vec<f64>>::new();
    let mut final_r: Vec<f32> = Vec::new();

    for day in 0..=days {
        // Snapshot of every card at the start of the day. It also provides the
        // retrievability used to order and select the day's cards.
        let query_inputs: Vec<_> = cards
            .iter()
            .map(|card| simulation_query_input_for_card(card, tr, day))
            .collect();
        let predictions = worker.workload_query_predictions_for_inputs(&query_inputs, &states);
        invalid_r += predictions
            .iter()
            .filter(|p| !valid_probability(p.retrievability))
            .count() as u64;
        let r: Vec<f32> = predictions
            .iter()
            .map(|p| sanitize(p.retrievability))
            .collect();

        for index in 0..n {
            min_r[index] = min_r[index].min(r[index]);
            if day >= 1 {
                min_r_after_start[index] = min_r_after_start[index].min(r[index]);
            }
            if day >= 1 && !reviewed_prev[index] {
                let drop = prev_r[index] - r[index];
                drops.push(drop);
                max_drop_per_card[index] = max_drop_per_card[index].max(drop);
                if counterfactual && r_hat[index].is_finite() {
                    let revision = r[index] - r_hat[index];
                    revisions.push(revision);
                    min_revision_per_card[index] = min_revision_per_card[index].min(revision);
                }
            }
        }
        let r_true: Vec<f32> = (0..n).map(|index| true_of(index, r[index])).collect();
        if day >= 1 {
            for index in 0..n {
                min_true_after_start[index] = min_true_after_start[index].min(r_true[index]);
            }
        }
        if let Some(spec) = shock {
            for (group, (_, mask)) in spec.groups.iter().enumerate() {
                group_pred_series[group].push(masked_mean(&r, mask, true));
                group_true_series[group].push(masked_mean(&r_true, mask, true));
            }
        }
        let due: Vec<usize> = (0..n)
            .filter(|&index| !cards[index].suspended && cards[index].due_day <= day)
            .collect();
        let sorted_r = sorted(&r);
        let mut push = |name: &'static str, value: f64| series.entry(name).or_default().push(value);
        push("day", day as f64);
        push("mean_r", mean(&r));
        push("p5_r", quantile(&sorted_r, 0.05));
        push("frac_lt_07", fraction(&r, |v| v < 0.7));
        push("frac_lt_05", fraction(&r, |v| v < 0.5));
        push("backlog_before", due.len() as f64);
        if day == days {
            final_r = r;
            final_true = r_true;
            break;
        }

        // Selection happens from the day-start snapshot, like the production
        // sort before answering.
        let by_priority = |order: ReviewCardOrder| {
            let mut list = due.clone();
            list.sort_by_key(|&index| {
                simulation_review_priority(&cards[index], Some(&predictions[index]), order, tr)
            });
            list
        };
        let random_daily = || {
            let mut list = due.clone();
            list.sort_by(|&a, &b| {
                simulation_unit_hash(card_id(&cards, a), day, MIX_SALT)
                    .total_cmp(&simulation_unit_hash(card_id(&cards, b), day, MIX_SALT))
                    .then(card_id(&cards, a).cmp(&card_id(&cards, b)))
            });
            list
        };
        let limit = scenario.review_limit;
        let mut selected = match strategy.kind {
            Kind::Reference(order) => by_priority(order),
            Kind::RandomDaily => random_daily(),
            Kind::Mix(k) => mix_select(
                &by_priority(ReviewCardOrder::RetrievabilityAscending),
                &random_daily(),
                k,
                limit,
                &mut mix_counter,
                n,
            ),
            Kind::RoundRobinDeck => round_robin_select(
                &by_priority(ReviewCardOrder::RetrievabilityAscending),
                |index| cards[index].review_input.review_input.deck_id,
                limit,
            ),
            Kind::RoundRobinPreset => round_robin_select(
                &by_priority(ReviewCardOrder::RetrievabilityAscending),
                |index| cards[index].review_input.review_input.preset_id,
                limit,
            ),
        };
        selected.truncate(limit);
        let mut reviewed_today = vec![false; n];
        for &index in &selected {
            reviewed_today[index] = true;
        }
        // Deck concentration of the day's reviews.
        let mut deck_counts = HashMap::<Option<i64>, u32>::new();
        for &index in &selected {
            *deck_counts.entry(deck_of(&cards, index)).or_default() += 1;
        }
        for deck in deck_counts.keys() {
            let last = deck_last_day.get(deck).copied().unwrap_or(-1);
            let gap = deck_max_gap.entry(*deck).or_insert(0);
            *gap = (*gap).max(day - last - 1);
            deck_last_day.insert(*deck, day);
        }
        if !selected.is_empty() {
            let total = selected.len() as f64;
            let entropy: f64 = deck_counts
                .values()
                .map(|&c| {
                    let p = f64::from(c) / total;
                    -p * p.ln()
                })
                .sum();
            deck_distinct.push(deck_counts.len() as f64);
            if deck_cards.len() > 1 {
                deck_entropy.push(entropy / (deck_cards.len() as f64).ln());
            }
        }
        series
            .entry("backlog_after")
            .or_default()
            .push((due.len() - selected.len()) as f64);
        series
            .entry("reviews")
            .or_default()
            .push(selected.len() as f64);
        reviews_per_day.push(selected.len() as u32);

        // What each unreviewed card would be tomorrow had no state changed.
        if counterfactual {
            let stay: Vec<usize> = (0..n).filter(|&index| !reviewed_today[index]).collect();
            let inputs: Vec<_> = stay
                .iter()
                .map(|&index| simulation_query_input_for_card(&cards[index], tr, day + 1))
                .collect();
            let hat = worker.workload_query_predictions_for_inputs(&inputs, &states);
            r_hat.fill(f32::NAN);
            for (&index, prediction) in stay.iter().zip(&hat) {
                r_hat[index] = sanitize(prediction.retrievability);
            }
        }
        prev_r.copy_from_slice(&r);

        // Answer one card at a time (state update interval 1).
        for &index in &selected {
            let query = simulation_query_input_for_card(&cards[index], tr, day);
            let prediction = worker
                .workload_query_predictions_for_inputs(std::slice::from_ref(&query), &states)
                .pop()
                .unwrap();
            let r_at_review = sanitize(prediction.retrievability);
            let true_r = true_of(index, r_at_review);
            let probabilities = review_model.probabilities_for(true_r);
            let ease = simulation_grade(
                probabilities,
                card_id(&cards, index) ^ strategy.grade_seed.wrapping_mul(0x5851_F42D_4C95_7F2D),
                day,
                cards[index].reps,
            );
            let interval = worker.selected_answer_intervals_for_inputs(
                std::slice::from_ref(&query),
                &states,
                &[ease],
            )[0]
            .unwrap_or(1)
            .clamp(1, MAX_INTERVAL);
            let seconds = review_model.grade_seconds[(ease - 1) as usize];
            total_cost += f64::from(seconds);
            review_log.push((index, r_at_review, true_r, ease == 1));
            first_review_day[index].get_or_insert(day);
            review_rs.push(r_at_review);
            intervals.push(interval as f32);
            if !ever_reviewed[index] {
                first_intervals.push(interval as f32);
            }
            ever_reviewed[index] = true;
            let answer = simulation_answer_input(
                &cards[index],
                tr,
                day,
                ease,
                (seconds * 1000.0).round().max(1.0) as i64,
            );
            worker.apply_simulation_answer(&answer, &mut states)?;
            let card = &mut cards[index];
            card.last_review_day = day;
            card.due_day = day + i64::from(interval);
            card.interval_days = i64::from(interval);
            card.reps += 1;
            if ease == 1 {
                card.lapses += 1;
                lapses += 1;
            }
        }
        reviewed_prev = reviewed_today;
        if day % 15 == 14 {
            println!(
                "  [{}/{}] day {}/{} reviews={} elapsed={:.0}s",
                scenario.name,
                strategy.name,
                day + 1,
                days,
                review_rs.len(),
                started.elapsed().as_secs_f64()
            );
        }
    }

    // ---- aggregate ----
    let sorted_review_r = sorted(&review_rs);
    let sorted_final = sorted(&final_r);
    let sorted_min = sorted(&min_r);
    let sorted_min_after = sorted(&min_r_after_start);
    let sorted_drops = sorted(&drops);
    let sorted_revisions = sorted(&revisions);
    let sorted_max_drop = sorted(&max_drop_per_card);
    let sorted_min_revision = sorted(&min_revision_per_card);
    let days_since: Vec<f32> = cards
        .iter()
        .map(|card| (days - card.last_review_day) as f32)
        .collect();
    let sorted_days_since = sorted(&days_since);
    let distinct = ever_reviewed.iter().filter(|&&r| r).count();
    let sum_r: f64 = final_r.iter().map(|&v| f64::from(v)).sum();
    let revision_fraction = |predicate: &dyn Fn(f32) -> bool| fraction(&revisions, predicate);

    let revision_json = if counterfactual {
        json!({
            "card_days": revisions.len(),
            "mean": mean(&revisions),
            "mean_abs": mean(&revisions.iter().map(|v| v.abs()).collect::<Vec<_>>()),
            "frac_lt_m005": revision_fraction(&|v| v < -0.05),
            "frac_lt_m010": revision_fraction(&|v| v < -0.10),
            "frac_lt_m020": revision_fraction(&|v| v < -0.20),
            "frac_gt_p005": revision_fraction(&|v| v > 0.05),
            "p01": quantile(&sorted_revisions, 0.01),
            "p99": quantile(&sorted_revisions, 0.99),
            "per_card_min_p50": quantile(&sorted_min_revision, 0.50),
            "per_card_min_p10": quantile(&sorted_min_revision, 0.10),
            "per_card_min_p01": quantile(&sorted_min_revision, 0.01),
        })
    } else {
        Value::Null
    };

    let metrics = json!({
        "reviews": review_rs.len(),
        "total_cost_seconds": total_cost,
        "distinct_cards_reviewed": distinct,
        "lapses": lapses,
        "invalid_r_replaced": invalid_r,
        "r_at_review": {
            "mean": mean(&review_rs),
            "p5": quantile(&sorted_review_r, 0.05),
            "p10": quantile(&sorted_review_r, 0.10),
            "p25": quantile(&sorted_review_r, 0.25),
            "frac_lt_05": fraction(&review_rs, |v| v < 0.5),
            "frac_lt_07": fraction(&review_rs, |v| v < 0.7),
        },
        "assigned_interval_days": {
            "p10": quantile(&sorted(&intervals), 0.10),
            "p50": quantile(&sorted(&intervals), 0.50),
            "p90": quantile(&sorted(&intervals), 0.90),
            "first_review_p50": quantile(&sorted(&first_intervals), 0.50),
            "first_review_p90": quantile(&sorted(&first_intervals), 0.90),
        },
        "end": {
            "mean_r": mean(&final_r),
            "p5_r": quantile(&sorted_final, 0.05),
            "frac_lt_07": fraction(&final_r, |v| v < 0.7),
            "frac_lt_05": fraction(&final_r, |v| v < 0.5),
            "sum_r_memorized": sum_r,
            "backlog": cards.iter().filter(|c| c.due_day <= days).count(),
        },
        "per_card_min_r": {
            "frac_lt_07": fraction(&min_r, |v| v < 0.7),
            "frac_lt_05": fraction(&min_r, |v| v < 0.5),
            "p1": quantile(&sorted_min, 0.01),
            "p5": quantile(&sorted_min, 0.05),
            "p10": quantile(&sorted_min, 0.10),
        },
        "per_card_min_r_days_ge_1": {
            "frac_lt_07": fraction(&min_r_after_start, |v| v < 0.7),
            "frac_lt_05": fraction(&min_r_after_start, |v| v < 0.5),
            "p1": quantile(&sorted_min_after, 0.01),
            "p5": quantile(&sorted_min_after, 0.05),
            "p10": quantile(&sorted_min_after, 0.10),
        },
        "unreviewed_day_drop": {
            "card_days": drops.len(),
            "frac_gt_005": fraction(&drops, |v| v > 0.05),
            "frac_gt_010": fraction(&drops, |v| v > 0.10),
            "frac_gt_020": fraction(&drops, |v| v > 0.20),
            "p99": quantile(&sorted_drops, 0.99),
            "per_card_max": {
                "p50": quantile(&sorted_max_drop, 0.50),
                "p90": quantile(&sorted_max_drop, 0.90),
                "p99": quantile(&sorted_max_drop, 0.99),
                "max": quantile(&sorted_max_drop, 1.0),
            },
        },
        "revision_vs_counterfactual": revision_json,
        "days_since_last_review_at_end": {
            "max": quantile(&sorted_days_since, 1.0),
            "p95": quantile(&sorted_days_since, 0.95),
            "mean": mean(&days_since),
        },
        "series": series,
        "runtime_seconds": started.elapsed().as_secs_f64(),
    });
    let mut summary = BTreeMap::new();
    for (key, value) in [
        ("reviews", review_rs.len() as f64),
        ("cost_h", total_cost / 3600.0),
        ("distinct", distinct as f64),
        ("lapses", lapses as f64),
        ("rev_mean_r", mean(&review_rs)),
        ("rev_p5", quantile(&sorted_review_r, 0.05)),
        ("rev_lt05", fraction(&review_rs, |v| v < 0.5)),
        ("end_mean_r", mean(&final_r)),
        ("end_p5", quantile(&sorted_final, 0.05)),
        ("end_lt07", fraction(&final_r, |v| v < 0.7)),
        ("end_lt05", fraction(&final_r, |v| v < 0.5)),
        ("sum_r", sum_r),
        ("min_lt05", fraction(&min_r, |v| v < 0.5)),
        ("min1_lt05", fraction(&min_r_after_start, |v| v < 0.5)),
        ("min1_lt07", fraction(&min_r_after_start, |v| v < 0.7)),
        ("drop_p99", quantile(&sorted_drops, 0.99)),
        ("drop_gt010", fraction(&drops, |v| v > 0.10)),
        (
            "revis_lt_m005",
            if counterfactual {
                revision_fraction(&|v| v < -0.05)
            } else {
                f64::NAN
            },
        ),
        ("max_days", quantile(&sorted_days_since, 1.0)),
        ("p95_days", quantile(&sorted_days_since, 0.95)),
    ] {
        summary.insert(key, value);
    }
    let mut metrics = metrics;
    let mut shock_summary = BTreeMap::<String, f64>::new();
    let mut shock_json = Value::Null;
    if let Some(spec) = shock {
        let mut groups_json = serde_json::Map::new();
        for (group, (name, mask)) in spec.groups.iter().enumerate() {
            let mut sides = serde_json::Map::new();
            for (side, member) in [("m", true), ("u", false)] {
                let reviews: Vec<_> = review_log
                    .iter()
                    .filter(|(index, ..)| mask[*index] == member)
                    .collect();
                let true_rs: Vec<f32> = reviews.iter().map(|r| r.2).collect();
                let pred_rs: Vec<f32> = reviews.iter().map(|r| r.1).collect();
                let sorted_true = sorted(&true_rs);
                let lapse_rate = if reviews.is_empty() {
                    f64::NAN
                } else {
                    reviews.iter().filter(|r| r.3).count() as f64 / reviews.len() as f64
                };
                let pick = |values: &[f32]| -> Vec<f32> {
                    values
                        .iter()
                        .zip(mask)
                        .filter(|(_, &m)| m == member)
                        .map(|(&v, _)| v)
                        .collect()
                };
                let min_true = pick(&min_true_after_start);
                let end_true = pick(&final_true);
                let end_pred = pick(&final_r);
                let sorted_end = sorted(&end_true);
                let mut values: Vec<(&str, f64)> = vec![
                    ("cards", min_true.len() as f64),
                    ("reviews", reviews.len() as f64),
                    ("lapse_rate", lapse_rate),
                    ("true_r_mean", mean(&true_rs)),
                    ("true_r_p5", quantile(&sorted_true, 0.05)),
                    ("true_r_lt05", fraction(&true_rs, |v| v < 0.5)),
                    ("pred_r_at_review_mean", mean(&pred_rs)),
                    ("min_true_lt05", fraction(&min_true, |v| v < 0.5)),
                    ("min_true_lt07", fraction(&min_true, |v| v < 0.7)),
                    ("end_true_mean", mean(&end_true)),
                    ("end_true_p5", quantile(&sorted_end, 0.05)),
                    ("end_pred_mean", mean(&end_pred)),
                ];
                if member {
                    let members = mask.iter().filter(|&&m| m).count();
                    for (label, fraction_wanted) in
                        [("t25", 0.25), ("t50", 0.50), ("t75", 0.75), ("t90", 0.90)]
                    {
                        let needed = (fraction_wanted * members as f64).ceil() as usize;
                        let reached = (0..days).find(|&day| {
                            first_review_day
                                .iter()
                                .zip(mask)
                                .filter(|(d, &m)| m && d.is_some_and(|d| d <= day))
                                .count()
                                >= needed
                        });
                        // Censored at days + 1 when the share is never reached.
                        values.push((label, reached.map_or(days as f64 + 1.0, |d| d as f64)));
                    }
                    let reviewed_by_30 = first_review_day
                        .iter()
                        .zip(mask)
                        .filter(|(d, &m)| m && d.is_some_and(|d| d < 30))
                        .count();
                    values.push((
                        "reviewed_by_d30",
                        reviewed_by_30 as f64 / members.max(1) as f64,
                    ));
                    for day in [7usize, 14, 30] {
                        let label = match day {
                            7 => "pred_d7",
                            14 => "pred_d14",
                            _ => "pred_d30",
                        };
                        values.push((label, group_pred_series[group][day]));
                    }
                }
                let mut side_json = serde_json::Map::new();
                for (key, value) in values {
                    side_json.insert(key.into(), json!(value));
                    shock_summary.insert(format!("{name}.{side}.{key}"), value);
                }
                sides.insert(side.into(), Value::Object(side_json));
            }
            sides.insert("pred_r_series".into(), json!(group_pred_series[group]));
            sides.insert("true_r_series".into(), json!(group_true_series[group]));
            groups_json.insert((*name).into(), Value::Object(sides));
        }
        // Deck concentration.
        let mut gaps: Vec<f32> = deck_cards
            .iter()
            .filter(|(_, &count)| count >= 10)
            .map(|(deck, _)| {
                let last = deck_last_day.get(deck).copied().unwrap_or(-1);
                deck_max_gap
                    .get(deck)
                    .copied()
                    .unwrap_or(0)
                    .max(days - 1 - last) as f32
            })
            .collect();
        gaps.sort_by(f32::total_cmp);
        let concentration = [
            ("distinct_decks_per_day", mean_f64(&deck_distinct)),
            ("norm_entropy", mean_f64(&deck_entropy)),
            ("gap_p50", quantile(&gaps, 0.5)),
            ("gap_p90", quantile(&gaps, 0.9)),
            ("gap_max", quantile(&gaps, 1.0)),
        ];
        let mut concentration_json = serde_json::Map::new();
        for (key, value) in concentration {
            concentration_json.insert(key.into(), json!(value));
            shock_summary.insert(format!("conc.{key}"), value);
        }
        concentration_json.insert("decks_total".into(), json!(deck_cards.len()));
        concentration_json.insert("decks_with_10_cards".into(), json!(gaps.len()));
        shock_json = json!({"groups": groups_json, "deck_concentration": concentration_json});
    }
    metrics["shock"] = shock_json;
    Ok(RunResult {
        metrics,
        reviews_per_day,
        summary,
        shock_summary,
    })
}

// ---------------------------------------------------------------------------
// Real-data staleness check
// ---------------------------------------------------------------------------

fn staleness_bin(days: i64) -> usize {
    match days {
        ..=0 => 0,
        1 => 1,
        2..=3 => 2,
        4..=7 => 3,
        8..=14 => 4,
        15..=30 => 5,
        31..=90 => 6,
        _ => 7,
    }
}

#[derive(Default, Clone, Copy)]
struct BinStats {
    n: usize,
    sum_pred: f64,
    sum_actual: f64,
    loss: f64,
}

impl BinStats {
    fn add(&mut self, prediction: f32, passed: bool) {
        let p = f64::from(prediction).clamp(1e-6, 1.0 - 1e-6);
        self.n += 1;
        self.sum_pred += p;
        self.sum_actual += f64::from(u8::from(passed));
        self.loss -= if passed { p.ln() } else { (1.0 - p).ln() };
    }

    fn to_json(self, label: &str) -> Value {
        let n = self.n.max(1) as f64;
        json!({
            "bin": label, "n": self.n,
            "mean_pred": self.sum_pred / n,
            "actual_pass_rate": self.sum_actual / n,
            "calibration_gap": (self.sum_pred - self.sum_actual) / n,
            "log_loss": self.loss / n,
        })
    }
}

fn staleness_report(rows: &[Row], predictions: &[(usize, f32)]) -> Value {
    assert_eq!(rows.len(), predictions.len());
    // [scope][subset][bin]
    let mut stats = [[[BinStats::default(); 8]; 2]; 3];
    let mut deck_days = HashMap::<i64, i64>::new();
    let mut preset_days = HashMap::<i64, i64>::new();
    // Per note: the two most recent reviews, from different cards.
    let mut note_days = HashMap::<i64, [(i64, i64); 2]>::new();
    let mut seen = HashSet::new();
    for (row, &(_, prediction)) in rows.iter().zip(predictions) {
        let (card, day) = (row.1, row.8);
        let has_previous = !seen.insert(card);
        let note_stale = row.2.and_then(|note| {
            note_days.get(&note).and_then(|entries| {
                entries
                    .iter()
                    .find(|(_, other)| *other != card && *other != 0)
                    .map(|(last, _)| day - last)
            })
        });
        let staleness = [
            row.3.and_then(|id| deck_days.get(&id)).map(|d| day - d),
            row.4.and_then(|id| preset_days.get(&id)).map(|d| day - d),
            note_stale,
        ];
        if has_previous && row.9 >= 1 && row.11 {
            let passed = row.5 != 1;
            let in_window = (7..=30).contains(&row.9);
            for (scope, stale) in staleness.iter().enumerate() {
                if let Some(stale) = stale {
                    let bin = staleness_bin(*stale);
                    stats[scope][0][bin].add(prediction, passed);
                    if in_window {
                        stats[scope][1][bin].add(prediction, passed);
                    }
                }
            }
        }
        if let Some(id) = row.3 {
            let entry = deck_days.entry(id).or_insert(day);
            *entry = (*entry).max(day);
        }
        if let Some(id) = row.4 {
            let entry = preset_days.entry(id).or_insert(day);
            *entry = (*entry).max(day);
        }
        if let Some(note) = row.2 {
            let entries = note_days.entry(note).or_insert([(0, 0); 2]);
            if entries[0].1 == card {
                entries[0].0 = entries[0].0.max(day);
            } else {
                entries[1] = entries[0];
                entries[0] = (day, card);
            }
        }
    }
    let scopes = ["deck", "preset", "note_other_cards"];
    let subsets = ["all_elapsed_ge_1", "elapsed_7_30"];
    let mut report = serde_json::Map::new();
    for (scope_index, scope) in scopes.iter().enumerate() {
        let mut by_subset = serde_json::Map::new();
        for (subset_index, subset) in subsets.iter().enumerate() {
            let bins = &stats[scope_index][subset_index];
            let mut total = BinStats::default();
            for bin in bins {
                total.n += bin.n;
                total.sum_pred += bin.sum_pred;
                total.sum_actual += bin.sum_actual;
                total.loss += bin.loss;
            }
            let mut values: Vec<_> = bins
                .iter()
                .zip(BIN_LABELS)
                .map(|(bin, label)| bin.to_json(label))
                .collect();
            values.push(total.to_json("total"));
            by_subset.insert((*subset).into(), Value::Array(values));
        }
        report.insert((*scope).into(), Value::Object(by_subset));
    }
    Value::Object(report)
}

fn print_staleness(report: &Value) {
    println!("\n== Real-data staleness check (days since last review in scope) ==");
    for (scope, subsets) in report.as_object().unwrap() {
        for (subset, bins) in subsets.as_object().unwrap() {
            println!("-- {scope} / {subset}");
            println!(
                "{:>6} {:>8} {:>9} {:>9} {:>9} {:>9}",
                "bin", "n", "mean_pred", "actual", "gap", "logloss"
            );
            for bin in bins.as_array().unwrap() {
                println!(
                    "{:>6} {:>8} {:>9.4} {:>9.4} {:>+9.4} {:>9.4}",
                    bin["bin"].as_str().unwrap(),
                    bin["n"],
                    bin["mean_pred"].as_f64().unwrap(),
                    bin["actual_pass_rate"].as_f64().unwrap(),
                    bin["calibration_gap"].as_f64().unwrap(),
                    bin["log_loss"].as_f64().unwrap(),
                );
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Loading and driver
// ---------------------------------------------------------------------------

fn rows_to_reviews(rows: &[Row]) -> Vec<ReviewInput> {
    rows.iter()
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
        .collect()
}

fn parse_inputs(values: &[Value]) -> Vec<RwkvWorkloadSimulationInput> {
    values
        .iter()
        .map(|row| {
            let row = row.as_array().expect("input row");
            assert_eq!(row.len(), 20, "workload input rows have 20 fields");
            let int = |i: usize| row[i].as_i64();
            let target = |i: usize| row[i].as_f64().map(|v| v as f32);
            RwkvWorkloadSimulationInput {
                review_input: ReviewInput {
                    card_id: int(0).unwrap(),
                    note_id: int(1),
                    deck_id: int(2),
                    preset_id: int(3),
                    is_query: row[4].as_bool().unwrap(),
                    ease: int(5).map(|v| v as u8),
                    duration_millis: int(6),
                    card_type: int(7),
                    day_offset: int(8),
                    current_elapsed_days: int(9),
                    current_elapsed_seconds: int(10),
                    target_retentions: [target(11), target(12), target(13), target(14)],
                    enforce_grade_order: row[15].as_bool().unwrap(),
                },
                interval_days: int(16),
                ease_factor: int(17),
                reps: int(18),
                lapses: int(19),
            }
        })
        .collect()
}

fn print_summary(scenario: &str, rows: &[(String, BTreeMap<&'static str, f64>)]) {
    let columns = [
        "reviews",
        "cost_h",
        "distinct",
        "lapses",
        "rev_mean_r",
        "rev_p5",
        "rev_lt05",
        "end_mean_r",
        "end_p5",
        "end_lt07",
        "end_lt05",
        "sum_r",
        "min_lt05",
        "min1_lt05",
        "min1_lt07",
        "drop_p99",
        "revis_lt_m005",
        "max_days",
        "p95_days",
    ];
    println!("\n== {scenario} ==");
    print!("{:<21}", "strategy");
    for column in columns {
        print!(" {column:>10}");
    }
    println!();
    for (name, summary) in rows {
        print!("{name:<21}");
        for column in columns {
            let value = summary[column];
            if value.abs() >= 1000.0 {
                print!(" {value:>10.0}");
            } else {
                print!(" {value:>10.4}");
            }
        }
        println!();
    }
}

fn write_output(output: &str, results: &serde_json::Map<String, Value>) {
    fs::write(output, serde_json::to_vec_pretty(results).unwrap()).unwrap();
}

#[test]
fn review_order_selection_mix_and_round_robin() {
    // Cards 0..6, ascending R order is the identity, random order is reversed.
    let asc: Vec<usize> = (0..6).collect();
    let random: Vec<usize> = (0..6).rev().collect();
    let mut counter = 0;
    // k = 2: slots alternate asc, random, asc, random...
    assert_eq!(
        mix_select(&asc, &random, 2, 4, &mut counter, 6),
        vec![0, 5, 1, 4]
    );
    assert_eq!(counter, 4);
    // The counter persists: next slot is asc (counter 4 % 2 == 0).
    assert_eq!(mix_select(&asc, &random, 2, 1, &mut counter, 6), vec![0]);
    assert_eq!(counter, 5);
    // Never more than the due cards, and no duplicates.
    let mut counter = 0;
    let all = mix_select(&asc, &random, 4, 100, &mut counter, 6);
    assert_eq!(all.len(), 6);
    assert_eq!(all.iter().collect::<HashSet<_>>().len(), 6);
    // Groups by parity; the group of card 0 has the lowest R.
    let selected = round_robin_select(&asc, |i| Some((i % 2) as i64), 5);
    assert_eq!(selected, vec![0, 1, 2, 3, 4]);
    let selected = round_robin_select(&asc, |i| Some(i64::from(i >= 4)), 5);
    assert_eq!(selected, vec![0, 4, 1, 5, 2]);
}

#[test]
fn review_order_staleness_bins() {
    assert_eq!(
        [0, 1, 2, 3, 4, 7, 8, 14, 15, 30, 31, 90, 91].map(staleness_bin),
        [0, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7]
    );
}

type Summaries = BTreeMap<&'static str, f64>;

/// Per scenario and strategy, every replicate's summary.
type Collected = BTreeMap<(String, String), Vec<Summaries>>;

/// Per scenario: strategy name, mean summary, sd summary.
type SummaryTables = BTreeMap<String, Vec<(String, Summaries, Summaries)>>;

fn mean_sd(values: &[f64]) -> (f64, f64) {
    let n = values.len() as f64;
    let mean = values.iter().sum::<f64>() / n;
    let variance = if values.len() > 1 {
        values.iter().map(|v| (v - mean).powi(2)).sum::<f64>() / (n - 1.0)
    } else {
        f64::NAN
    };
    (mean, variance.sqrt())
}

fn aggregate(collected: &Collected) -> (Value, SummaryTables) {
    let mut json = serde_json::Map::new();
    let mut tables = SummaryTables::new();
    for ((scenario, strategy), replicates) in collected {
        let mut means = Summaries::new();
        let mut sds = Summaries::new();
        let mut metrics = serde_json::Map::new();
        for &key in replicates[0].keys() {
            let values: Vec<f64> = replicates.iter().map(|r| r[key]).collect();
            let (mean, sd) = mean_sd(&values);
            means.insert(key, mean);
            sds.insert(key, sd);
            metrics.insert(
                key.into(),
                json!({"mean": mean, "sd": sd, "n": values.len(), "values": values}),
            );
        }
        json.entry(scenario.clone())
            .or_insert_with(|| json!({}))
            .as_object_mut()
            .unwrap()
            .insert(strategy.clone(), Value::Object(metrics));
        tables
            .entry(scenario.clone())
            .or_default()
            .push((strategy.clone(), means, sds));
    }
    (Value::Object(json), tables)
}

#[test]
#[ignore]
fn rwkv_review_order_metrics() {
    let input = env::var("ANKI_RWKV_REVIEW_ORDER_INPUT").expect("set ANKI_RWKV_REVIEW_ORDER_INPUT");
    let output =
        env::var("ANKI_RWKV_REVIEW_ORDER_OUTPUT").expect("set ANKI_RWKV_REVIEW_ORDER_OUTPUT");
    assert_ne!(Path::new(&input), Path::new(&output));
    // The simulated stream feeds the shared deck/preset/global state and the
    // "reviews today" feature, so thousands of reviews a day push the model far
    // outside its history and R collapses. Simulate disjoint hash subsets of
    // the review cards (1/10 by default) so the daily volume stays realistic,
    // and average the replicates. MODULUS=1 simulates every card.
    let sample_modulus: u64 = env::var("ANKI_RWKV_REVIEW_ORDER_SAMPLE_MODULUS")
        .ok()
        .and_then(|v| v.parse().ok())
        .unwrap_or(10);
    let residues: Vec<u64> = env::var("ANKI_RWKV_REVIEW_ORDER_SAMPLE_RESIDUES")
        .unwrap_or_else(|_| "0,1,2,3,4".into())
        .split(',')
        .map(|v| v.trim().parse().unwrap())
        .filter(|&r| r < sample_modulus)
        .collect();
    assert!(!residues.is_empty());
    let counterfactual = env::var("ANKI_RWKV_REVIEW_ORDER_COUNTERFACTUAL").as_deref() != Ok("0");
    let only_calibration =
        env::var("ANKI_RWKV_REVIEW_ORDER_ONLY_CALIBRATION").as_deref() == Ok("1");
    let input_bytes = fs::read(&input).unwrap();
    let mut source: Value = serde_json::from_slice(&input_bytes).unwrap();
    let rows: Vec<Row> =
        serde_json::from_value(source.as_object_mut().unwrap().remove("rows").unwrap()).unwrap();
    assert!(rows.windows(2).all(|pair| pair[0].0 < pair[1].0));
    let all_inputs = parse_inputs(
        source["inputs"]
            .as_array()
            .expect("inputs array of workload rows"),
    );
    println!(
        "history rows={} review cards={} sample 1/{sample_modulus} residues={residues:?} counterfactual={counterfactual}",
        rows.len(),
        all_inputs.len()
    );

    // (a) warm up, keeping the per-review predictions.
    let reviews = rows_to_reviews(&rows);
    let weights = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join(MODEL_PATH);
    let mut inference = super::state_scope_bench::load_for_scopes(
        weights.clone(),
        StatePrecision::Int8,
        [false; 3],
    );
    let started = Instant::now();
    let predictions = inference.warm_up_reviews(reviews, true).unwrap();
    println!(
        "warm-up: {:.1}s, scopes={}",
        started.elapsed().as_secs_f64(),
        scope_sizes(&inference)
    );

    let mut results = serde_json::Map::new();
    results.insert(
        "meta".into(),
        json!({
            "backup_sha256": source["backup_sha256"],
            "input_blake3": blake3::hash(&input_bytes).to_hex().to_string(),
            "model_blake3": blake3::hash(&fs::read(&weights).unwrap()).to_hex().to_string(),
            "history_rows": rows.len(), "review_cards_total": all_inputs.len(),
            "sample_modulus": sample_modulus, "sample_residues": residues,
            "target_retention": TARGET_RETENTION, "grade_seconds": GRADE_SECONDS,
            "state_update_interval": 1, "max_interval": MAX_INTERVAL,
            "counterfactual": counterfactual, "state_precision": "int8_card_note",
        }),
    );

    // (f) real-data staleness check.
    let staleness = staleness_report(&rows, &predictions);
    print_staleness(&staleness);
    results.insert("real_data_staleness".into(), staleness);
    write_output(&output, &results);

    let all_strategies = strategies();
    let mut collected = Collected::new();
    let mut replicates = serde_json::Map::new();
    let mut calibration_a = Vec::new();
    for &residue in &residues {
        let inputs: Vec<_> = all_inputs
            .iter()
            .filter(|i| {
                sample_modulus == 1
                    || (simulation_unit_hash(i.review_input.card_id, 0, 123) * 1e9) as u64
                        % sample_modulus
                        == residue
            })
            .cloned()
            .collect();
        println!(
            "\n######## replicate residue {residue}: {} cards",
            inputs.len()
        );
        let mut replicate = serde_json::Map::new();
        replicate.insert("cards".into(), json!(inputs.len()));

        // (d) calibration run.
        let unlimited = Scenario {
            name: "unlimited",
            days: 60,
            review_limit: usize::MAX,
            age_days: 0,
        };
        let started = Instant::now();
        let calibration = run_strategy(
            &inference,
            &inputs,
            &unlimited,
            &all_strategies[0],
            counterfactual,
            None,
        )
        .unwrap();
        let total: u32 = calibration.reviews_per_day.iter().sum();
        let a = f64::from(total) / unlimited.days as f64;
        let tail = &calibration.reviews_per_day[30..];
        let steady = f64::from(tail.iter().sum::<u32>()) / tail.len() as f64;
        println!(
            "unlimited calibration: A={a:.2} reviews/day (last-30-day mean {steady:.2}), {:.0}s",
            started.elapsed().as_secs_f64()
        );
        calibration_a.push(a);
        replicate.insert(
            "calibration".into(),
            json!({
                "A": a, "last_30_day_mean": steady,
                "reviews_per_day": calibration.reviews_per_day,
                "metrics": calibration.metrics,
            }),
        );
        replicates.insert(
            format!("residue_{residue}"),
            Value::Object(replicate.clone()),
        );
        results.insert("replicates".into(), Value::Object(replicates.clone()));
        write_output(&output, &results);
        if only_calibration {
            continue;
        }

        let scenarios = [
            Scenario {
                name: "chronic_60",
                days: 90,
                review_limit: (0.6 * a).round() as usize,
                age_days: 0,
            },
            Scenario {
                name: "vacation_30",
                days: 90,
                review_limit: a.round() as usize,
                age_days: 30,
            },
        ];
        for scenario in scenarios {
            println!(
                "\n### residue {residue} scenario {} (limit {}/day, {} days, age +{})",
                scenario.name, scenario.review_limit, scenario.days, scenario.age_days
            );
            let scenario_started = Instant::now();
            let runs: Vec<(String, RunResult)> = all_strategies
                .par_iter()
                .map(|strategy| {
                    let started = Instant::now();
                    let result = run_strategy(
                        &inference,
                        &inputs,
                        &scenario,
                        strategy,
                        counterfactual,
                        None,
                    )
                    .unwrap();
                    println!(
                        "[{residue}/{}/{}] done in {:.0}s",
                        scenario.name,
                        strategy.name,
                        started.elapsed().as_secs_f64()
                    );
                    (strategy.name.clone(), result)
                })
                .collect();
            let mut by_strategy = serde_json::Map::new();
            let mut summaries = Vec::new();
            for (name, run) in &runs {
                by_strategy.insert(name.clone(), run.metrics.clone());
                summaries.push((name.clone(), run.summary.clone()));
                collected
                    .entry((scenario.name.to_string(), name.clone()))
                    .or_default()
                    .push(run.summary.clone());
            }
            replicate.insert(
                scenario.name.into(),
                json!({
                    "review_limit": scenario.review_limit, "days": scenario.days,
                    "age_days": scenario.age_days, "strategies": by_strategy,
                    "runtime_seconds": scenario_started.elapsed().as_secs_f64(),
                }),
            );
            replicates.insert(
                format!("residue_{residue}"),
                Value::Object(replicate.clone()),
            );
            results.insert("replicates".into(), Value::Object(replicates.clone()));
            write_output(&output, &results);
            print_summary(&format!("residue {residue} {}", scenario.name), &summaries);
        }
        let (aggregate_json, _) = aggregate(&collected);
        results.insert("aggregate_over_replicates".into(), aggregate_json);
        results.insert("replicate_A".into(), json!(calibration_a));
        write_output(&output, &results);
    }

    if !collected.is_empty() {
        let (_, tables) = aggregate(&collected);
        println!("\nA per replicate: {calibration_a:?}");
        for (scenario, rows) in &tables {
            let means: Vec<_> = rows
                .iter()
                .map(|(n, m, _)| (n.clone(), m.clone()))
                .collect();
            let sds: Vec<_> = rows
                .iter()
                .map(|(n, _, s)| (n.clone(), s.clone()))
                .collect();
            print_summary(
                &format!("MEAN over {} replicates: {scenario}", residues.len()),
                &means,
            );
            print_summary(&format!("SD over replicates: {scenario}"), &sds);
        }
    }
}

// ---------------------------------------------------------------------------
// Real-history drop check
// ---------------------------------------------------------------------------

/// Largest fall `earlier - later` between two snapshots at most `window` days
/// apart.
fn max_fall_within(snapshots: &[(i64, f32)], window: i64) -> f32 {
    let mut best = 0.0f32;
    for (j, later) in snapshots.iter().enumerate() {
        for earlier in &snapshots[..j] {
            if later.0 - earlier.0 <= window {
                best = best.max(earlier.1 - later.1);
            }
        }
    }
    best
}

#[test]
fn review_order_max_fall_window() {
    let snapshots = [(1, 0.9), (2, 0.85), (5, 0.8), (12, 0.4)];
    // 0.9 -> 0.4 spans 11 days, 0.8 -> 0.4 spans 7.
    assert!((max_fall_within(&snapshots, 7) - 0.4).abs() < 1e-6);
    assert!((max_fall_within(&snapshots, 3) - 0.05).abs() < 1e-6);
    assert!((max_fall_within(&snapshots, 11) - 0.5).abs() < 1e-6);
}

struct DropEvent {
    card: usize,
    day: i64,
    before: f32,
    after: f32,
    counterfactual: f32,
}

struct Stretch {
    peak: f32,
    fall_7d: f32,
    r_review: f32,
    passed: bool,
    /// The card's previous review is older than the replayed window.
    censored: bool,
}

fn distribution(values: &[f32]) -> Value {
    let values = sorted(values);
    json!({
        "n": values.len(), "mean": mean(&values),
        "p01": quantile(&values, 0.01), "p05": quantile(&values, 0.05),
        "p25": quantile(&values, 0.25), "p50": quantile(&values, 0.50),
        "p75": quantile(&values, 0.75), "p95": quantile(&values, 0.95),
        "p99": quantile(&values, 0.99), "min": quantile(&values, 0.0),
        "max": quantile(&values, 1.0),
    })
}

fn day_activity(
    rows: &[Row],
    by_day: &BTreeMap<i64, Vec<usize>>,
    day: i64,
    (note_id, deck_id, preset_id): (Option<i64>, Option<i64>, Option<i64>),
) -> Value {
    let (mut note, mut deck, mut preset, mut all) = ([0u32; 2], [0u32; 2], [0u32; 2], [0u32; 2]);
    let (mut learning_new, mut relearning) = (0u32, 0u32);
    for &index in by_day.get(&day).map(Vec::as_slice).unwrap_or(&[]) {
        let other = &rows[index];
        let lapse = usize::from(other.5 == 1);
        for (counts, same) in [
            (&mut note, note_id.is_some() && other.2 == note_id),
            (&mut deck, deck_id.is_some() && other.3 == deck_id),
            (&mut preset, preset_id.is_some() && other.4 == preset_id),
            (&mut all, true),
        ] {
            if same {
                counts[0] += 1;
                counts[1] += lapse as u32;
            }
        }
        learning_new += u32::from(matches!(other.7, 0 | 1));
        relearning += u32::from(other.7 == 3);
    }
    json!({
        "note": {"answers": note[0], "lapses": note[1]},
        "deck": {"answers": deck[0], "lapses": deck[1]},
        "preset": {"answers": preset[0], "lapses": preset[1]},
        "collection": {"answers": all[0], "lapses": all[1],
            "learning_or_new_answers": learning_new, "relearning_answers": relearning},
    })
}

#[test]
#[ignore]
fn rwkv_review_order_real_drops() {
    const WINDOW_DAYS: i64 = 90;
    let input = env::var("ANKI_RWKV_REVIEW_ORDER_INPUT").expect("set ANKI_RWKV_REVIEW_ORDER_INPUT");
    let output =
        env::var("ANKI_RWKV_REVIEW_ORDER_OUTPUT").expect("set ANKI_RWKV_REVIEW_ORDER_OUTPUT");
    assert_ne!(Path::new(&input), Path::new(&output));
    let input_bytes = fs::read(&input).unwrap();
    let mut source: Value = serde_json::from_slice(&input_bytes).unwrap();
    let rows: Vec<Row> =
        serde_json::from_value(source.as_object_mut().unwrap().remove("rows").unwrap()).unwrap();
    assert!(rows.windows(2).all(|pair| pair[0].0 < pair[1].0));
    assert!(
        rows.windows(2).all(|pair| pair[0].8 <= pair[1].8),
        "history day offsets must not decrease"
    );
    let inputs = parse_inputs(source["inputs"].as_array().expect("inputs"));
    let last_history_day = rows.last().unwrap().8;
    let today = source["today"]
        .as_i64()
        .unwrap_or(last_history_day)
        .max(last_history_day);
    let start_day = last_history_day - (WINDOW_DAYS - 1);
    let reviews = rows_to_reviews(&rows);
    let weights = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join(MODEL_PATH);
    let load = || {
        super::state_scope_bench::load_for_scopes(weights.clone(), StatePrecision::Int8, [false; 3])
    };
    println!(
        "rows={} tracked-candidate review cards={} window days {start_day}..={today} (history ends {last_history_day})",
        rows.len(),
        inputs.len()
    );

    // One-shot replay: reference predictions for the parity check.
    let started = Instant::now();
    let reference = load().warm_up_reviews(reviews.clone(), true).unwrap();
    println!(
        "one-shot reference warm-up {:.1}s",
        started.elapsed().as_secs_f64()
    );

    let mut by_day = BTreeMap::<i64, Vec<usize>>::new();
    for (index, row) in rows.iter().enumerate() {
        by_day.entry(row.8).or_default().push(index);
    }
    let prefix_end = rows.partition_point(|row| row.8 < start_day);
    let mut inference = load();
    let started = Instant::now();
    inference
        .warm_up_reviews(reviews[..prefix_end].to_vec(), false)
        .unwrap();
    println!(
        "prefix warm-up ({prefix_end} rows) {:.1}s",
        started.elapsed().as_secs_f64()
    );

    let card_index: HashMap<i64, usize> = inputs
        .iter()
        .enumerate()
        .map(|(index, input)| (input.review_input.card_id, index))
        .collect();
    let n = inputs.len();
    let bases: Vec<ReviewInput> = inputs
        .iter()
        .map(|input| {
            let mut base = input.review_input.clone();
            base.day_offset = Some(0);
            base
        })
        .collect();
    let tr = TARGET_RETENTION;
    let mut last_review: Vec<Option<i64>> = vec![None; n];
    for row in &rows[..prefix_end] {
        if let Some(&index) = card_index.get(&row.1) {
            last_review[index] = Some(row.8);
        }
    }
    let mut prev_r: Vec<Option<f32>> = vec![None; n];
    let mut prev_counterfactual = vec![f32::NAN; n];
    let mut reviewed_prev = vec![false; n];
    let mut snapshots: Vec<Vec<(i64, f32)>> = vec![Vec::new(); n];
    let mut events: Vec<DropEvent> = Vec::new();
    let mut stretches: Vec<Stretch> = Vec::new();
    let mut within_day: Vec<f32> = Vec::new();
    let mut within_day_high_start: Vec<f32> = Vec::new();
    let mut parity_diffs: Vec<f32> = Vec::new();
    let mut per_day = Vec::new();
    let mut invalid = 0usize;
    let started = Instant::now();
    for day in start_day..=today {
        // Start-of-day R and tomorrow's counterfactual, before this day's rows.
        let tracked: Vec<usize> = (0..n).filter(|&i| last_review[i].is_some()).collect();
        let mut queries = Vec::with_capacity(tracked.len() * 2);
        for &i in &tracked {
            let elapsed = (day - last_review[i].unwrap()).max(0);
            queries.push(simulation_input(
                &bases[i], tr, day, elapsed, true, None, None,
            ));
        }
        for &i in &tracked {
            let elapsed = (day - last_review[i].unwrap()).max(0) + 1;
            queries.push(simulation_input(
                &bases[i],
                tr,
                day + 1,
                elapsed,
                true,
                None,
                None,
            ));
        }
        let predictions = {
            let mut worker = inference.workload_worker(inference.features.clone(), HashMap::new());
            worker.workload_query_predictions_for_inputs(
                &queries,
                &SimulationStates::new(&inference.warm_up_states),
            )
        };
        invalid += predictions
            .iter()
            .filter(|p| !valid_probability(p.retrievability))
            .count();
        let mut r = vec![f32::NAN; n];
        let mut counterfactual = vec![f32::NAN; n];
        for (position, &i) in tracked.iter().enumerate() {
            r[i] = sanitize(predictions[position].retrievability);
            counterfactual[i] = sanitize(predictions[tracked.len() + position].retrievability);
        }

        // Card-days with no review on the previous day.
        let mut day_drops = 0usize;
        for &i in &tracked {
            snapshots[i].push((day, r[i]));
            if let Some(before) = prev_r[i] {
                if !reviewed_prev[i] {
                    day_drops += 1;
                    events.push(DropEvent {
                        card: i,
                        day,
                        before,
                        after: r[i],
                        counterfactual: prev_counterfactual[i],
                    });
                }
            }
        }
        per_day.push(json!({
            "day": day, "tracked": tracked.len(), "mean_r": mean(&tracked.iter().map(|&i| r[i]).collect::<Vec<_>>()),
            "rows": by_day.get(&day).map_or(0, Vec::len), "card_days": day_drops,
        }));

        // Apply the day's rows, keeping the per-answer predictions.
        let day_rows = by_day.get(&day).cloned().unwrap_or_default();
        let mut reviewed_today = vec![false; n];
        if !day_rows.is_empty() {
            let (first, last) = (day_rows[0], *day_rows.last().unwrap());
            assert_eq!(last - first + 1, day_rows.len());
            let day_predictions = inference
                .warm_up_reviews(reviews[first..=last].to_vec(), true)
                .unwrap();
            assert_eq!(day_predictions.len(), day_rows.len());
            for (offset, &(local, prediction)) in day_predictions.iter().enumerate() {
                assert_eq!(local, offset);
                if day <= last_history_day {
                    parity_diffs.push((prediction - reference[first + offset].1).abs());
                }
                let row = &rows[first + offset];
                let Some(&i) = card_index.get(&row.1) else {
                    continue;
                };
                if reviewed_today[i] {
                    continue; // only the first answer of the day
                }
                reviewed_today[i] = true;
                if last_review[i].is_some() {
                    within_day.push(prediction - r[i]);
                    if r[i] >= 0.85 {
                        within_day_high_start.push(prediction - r[i]);
                    }
                    let stretch = &snapshots[i];
                    stretches.push(Stretch {
                        peak: stretch.iter().map(|s| s.1).fold(0.0, f32::max),
                        fall_7d: max_fall_within(stretch, 7),
                        r_review: sanitize(prediction),
                        passed: row.5 != 1,
                        censored: stretch[0].0 > last_review[i].unwrap() + 1,
                    });
                }
            }
        }
        for i in 0..n {
            if reviewed_today[i] {
                last_review[i] = Some(day);
                snapshots[i].clear();
            }
            prev_r[i] = (!r[i].is_nan()).then_some(r[i]);
        }
        prev_counterfactual = counterfactual;
        reviewed_prev = reviewed_today;
        if (day - start_day) % 15 == 14 {
            println!(
                "  day {day} tracked={} events={} elapsed={:.0}s",
                tracked.len(),
                events.len(),
                started.elapsed().as_secs_f64()
            );
        }
    }
    let runtime = started.elapsed().as_secs_f64();

    // ---- metrics ----
    let drops: Vec<f32> = events.iter().map(|e| e.before - e.after).collect();
    let revisions: Vec<f32> = events.iter().map(|e| e.after - e.counterfactual).collect();
    let sorted_drops = sorted(&drops);
    let sorted_revisions = sorted(&revisions);
    let mut order: Vec<usize> = (0..events.len()).collect();
    order.sort_by(|&a, &b| drops[b].total_cmp(&drops[a]));
    let top: Vec<Value> = order
        .iter()
        .take(25)
        .map(|&k| {
            let event = &events[k];
            let input = &inputs[event.card].review_input;
            json!({
                "card_id": input.card_id, "note_id": input.note_id,
                "deck_id": input.deck_id, "preset_id": input.preset_id,
                "day": event.day, "r_before": event.before, "r_after": event.after,
                "drop": event.before - event.after,
                "revision_part": event.after - event.counterfactual,
                "counterfactual_after": event.counterfactual,
                "activity_previous_day": day_activity(
                    &rows,
                    &by_day,
                    event.day - 1,
                    (input.note_id, input.deck_id, input.preset_id),
                ),
            })
        })
        .collect();
    let high_to_low = |before: f32, after: f32| {
        events
            .iter()
            .filter(|e| e.before >= before && e.after < after)
            .count()
    };
    let drop_stats = json!({
        "card_days": events.len(),
        "frac_gt_005": fraction(&drops, |v| v > 0.05),
        "frac_gt_010": fraction(&drops, |v| v > 0.10),
        "frac_gt_020": fraction(&drops, |v| v > 0.20),
        "frac_gt_030": fraction(&drops, |v| v > 0.30),
        "p99": quantile(&sorted_drops, 0.99), "max": quantile(&sorted_drops, 1.0),
        "count_gt_020": drops.iter().filter(|&&v| v > 0.20).count(),
        "count_start_ge_085_end_lt_060": high_to_low(0.85, 0.60),
        "count_start_ge_080_end_lt_050": high_to_low(0.80, 0.50),
        "count_start_ge_090_end_lt_070": high_to_low(0.90, 0.70),
    });
    let revision_stats = json!({
        "frac_lt_m005": fraction(&revisions, |v| v < -0.05),
        "frac_lt_m010": fraction(&revisions, |v| v < -0.10),
        "frac_lt_m020": fraction(&revisions, |v| v < -0.20),
        "frac_lt_m030": fraction(&revisions, |v| v < -0.30),
        "p01": quantile(&sorted_revisions, 0.01), "min": quantile(&sorted_revisions, 0.0),
        "p99": quantile(&sorted_revisions, 0.99), "max": quantile(&sorted_revisions, 1.0),
        "mean": mean(&revisions),
    });
    let summarize = |selected: Vec<&Stretch>| {
        let r: Vec<f32> = selected.iter().map(|s| s.r_review).collect();
        json!({
            "stretches": selected.len(),
            "r_at_review": distribution(&r),
            "frac_r_at_review_lt_05": fraction(&r, |v| v < 0.5),
            "pass_rate": selected.iter().filter(|s| s.passed).count() as f64 / selected.len().max(1) as f64,
            "mean_peak_r": mean(&selected.iter().map(|s| s.peak).collect::<Vec<_>>()),
        })
    };
    let slid = |threshold: f32| -> Vec<&Stretch> {
        stretches
            .iter()
            .filter(|s| s.fall_7d >= threshold)
            .collect()
    };
    let stretch_stats = json!({
        "all": summarize(stretches.iter().collect()),
        "left_censored": stretches.iter().filter(|s| s.censored).count(),
        "fell_ge_02_within_7d": summarize(slid(0.2)),
        "fell_ge_03_within_7d": summarize(slid(0.3)),
        "fell_lt_02_within_7d": summarize(stretches.iter().filter(|s| s.fall_7d < 0.2).collect()),
        "fell_ge_02_within_7d_uncensored": summarize(
            stretches.iter().filter(|s| s.fall_7d >= 0.2 && !s.censored).collect()),
        "peak_ge_085_then_review_lt_06": summarize(
            stretches.iter().filter(|s| s.peak >= 0.85 && s.r_review < 0.6).collect()),
        "fall_7d": distribution(&stretches.iter().map(|s| s.fall_7d).collect::<Vec<_>>()),
    });
    let within_stats = json!({
        "all": distribution(&within_day),
        "frac_lt_m005": fraction(&within_day, |v| v < -0.05),
        "frac_lt_m010": fraction(&within_day, |v| v < -0.10),
        "frac_lt_m020": fraction(&within_day, |v| v < -0.20),
        "start_r_ge_085": distribution(&within_day_high_start),
        "start_r_ge_085_frac_lt_m020": fraction(&within_day_high_start, |v| v < -0.20),
    });
    let sorted_parity = sorted(&parity_diffs);
    let parity = json!({
        "answers": parity_diffs.len(),
        "max_abs_diff": quantile(&sorted_parity, 1.0),
        "p99_abs_diff": quantile(&sorted_parity, 0.99),
        "mean_abs_diff": mean(&parity_diffs),
    });
    let result = json!({
        "meta": {
            "backup_sha256": source["backup_sha256"],
            "input_blake3": blake3::hash(&input_bytes).to_hex().to_string(),
            "window_start_day": start_day, "today": today,
            "last_history_day": last_history_day, "review_cards": n,
            "invalid_r_replaced": invalid, "runtime_seconds": runtime,
            "query_card_type_note": "queries use the input card_type (review) for every tracked card",
        },
        "chunked_vs_oneshot_prediction_parity": parity,
        "unreviewed_previous_day_card_days": {
            "drop": drop_stats, "revision_vs_counterfactual": revision_stats,
        },
        "top_25_drops": top,
        "multi_day_stretches": stretch_stats,
        "within_day_change_at_review": within_stats,
        "per_day": per_day,
    });
    fs::write(&output, serde_json::to_vec_pretty(&result).unwrap()).unwrap();

    println!("\n== parity (chunked vs one-shot warm-up predictions): {parity}");
    println!("== drops: {drop_stats}");
    println!("== revision: {revision_stats}");
    println!("== within-day change at review: {within_stats}");
    println!("== stretches: {stretch_stats}");
    for event in top.iter().take(10) {
        println!("top: {event}");
    }
}

// ---------------------------------------------------------------------------
// Shock experiment: the model mispredicts part of the collection
// ---------------------------------------------------------------------------

/// The deck with the median card count among decks with at least 30 cards
/// (the lower median, then the lower deck id on ties).
fn pick_shock_deck(inputs: &[RwkvWorkloadSimulationInput]) -> Option<(i64, usize)> {
    let mut counts = HashMap::<i64, usize>::new();
    for input in inputs {
        if let Some(deck) = input.review_input.deck_id {
            *counts.entry(deck).or_default() += 1;
        }
    }
    let mut decks: Vec<(usize, i64)> = counts
        .into_iter()
        .filter(|&(_, count)| count >= 30)
        .map(|(deck, count)| (count, deck))
        .collect();
    decks.sort();
    decks
        .get(decks.len().checked_sub(1)? / 2)
        .map(|&(count, deck)| (deck, count))
}

#[test]
fn review_order_shock_deck_choice() {
    let input = |deck: i64| RwkvWorkloadSimulationInput {
        review_input: ReviewInput {
            card_id: 1,
            note_id: None,
            deck_id: Some(deck),
            preset_id: None,
            is_query: true,
            ease: None,
            duration_millis: None,
            card_type: Some(2),
            day_offset: Some(0),
            current_elapsed_days: Some(1),
            current_elapsed_seconds: None,
            target_retentions: [None; 4],
            enforce_grade_order: true,
        },
        interval_days: None,
        ease_factor: None,
        reps: None,
        lapses: None,
    };
    // Counts: deck 1 -> 40, deck 2 -> 100, deck 3 -> 40, deck 4 -> 10 (too small).
    let mut inputs = Vec::new();
    for (deck, count) in [(1, 40), (2, 100), (3, 40), (4, 10)] {
        inputs.extend((0..count).map(|_| input(deck)));
    }
    // Sorted (count, id): (40,1) (40,3) (100,2); the median is deck 3.
    assert_eq!(pick_shock_deck(&inputs), Some((3, 40)));
    assert_eq!(pick_shock_deck(&inputs[..10]), None);
}

type ShockCollected = BTreeMap<(String, String, String), Vec<BTreeMap<String, f64>>>;

fn shock_aggregate(collected: &ShockCollected) -> Value {
    let mut out = serde_json::Map::new();
    for ((scenario, shock, strategy), replicates) in collected {
        let baseline = &collected[&(scenario.clone(), shock.clone(), "asc_r".to_string())];
        let mut metrics = serde_json::Map::new();
        for key in replicates[0].keys() {
            let values: Vec<f64> = replicates.iter().map(|r| r[key]).collect();
            let diffs: Vec<f64> = replicates
                .iter()
                .zip(baseline)
                .map(|(r, b)| r[key] - b[key])
                .collect();
            let (mean, sd) = mean_sd(&values);
            let (diff_mean, diff_sd) = mean_sd(&diffs);
            metrics.insert(
                key.clone(),
                json!({"mean": mean, "sd": sd, "values": values,
                    "paired_vs_asc_r": {"mean": diff_mean, "sd": diff_sd, "values": diffs}}),
            );
        }
        out.entry(scenario.clone())
            .or_insert_with(|| json!({}))
            .as_object_mut()
            .unwrap()
            .entry(shock.clone())
            .or_insert_with(|| json!({}))
            .as_object_mut()
            .unwrap()
            .insert(strategy.clone(), Value::Object(metrics));
    }
    Value::Object(out)
}

fn shock_columns(shock: &str) -> Vec<(String, String)> {
    let mut columns = Vec::new();
    for (label, key) in [
        ("t25", "m.t25"),
        ("t50", "m.t50"),
        ("t75", "m.t75"),
        ("t90", "m.t90"),
        ("rev@30d", "m.reviewed_by_d30"),
        ("trueR", "m.true_r_mean"),
        ("trueR<.5", "m.true_r_lt05"),
        ("lapse/rev", "m.lapse_rate"),
        ("min<.5", "m.min_true_lt05"),
        ("min<.7", "m.min_true_lt07"),
        ("endTrue", "m.end_true_mean"),
        ("endTrueP5", "m.end_true_p5"),
    ] {
        columns.push((label.to_string(), format!("{shock}.{key}")));
    }
    for (label, key) in [
        ("det_d7", "det_d7"),
        ("det_d14", "det_d14"),
        ("det_d30", "det_d30"),
        ("spill_end", "spill_end_true"),
        ("spill_min<.5", "spill_min_lt05"),
    ] {
        columns.push((label.to_string(), format!("{shock}.{key}")));
    }
    columns
}

fn print_shock_table(
    title: &str,
    collected: &ShockCollected,
    scenario: &str,
    shock: &str,
    columns: &[(String, String)],
    paired: bool,
) {
    println!("\n== {title} ==");
    print!("{:<21}", "strategy");
    for (label, _) in columns {
        print!(" {label:>12}");
    }
    println!();
    let strategies: Vec<&String> = collected
        .keys()
        .filter(|(sc, sh, _)| sc == scenario && sh == shock)
        .map(|(_, _, st)| st)
        .collect();
    let baseline = &collected[&(scenario.to_string(), shock.to_string(), "asc_r".to_string())];
    for strategy in strategies {
        let replicates = &collected[&(scenario.to_string(), shock.to_string(), strategy.clone())];
        print!("{strategy:<21}");
        for (_, key) in columns {
            let values: Vec<f64> = if paired {
                replicates
                    .iter()
                    .zip(baseline)
                    .map(|(r, b)| r[key] - b[key])
                    .collect()
            } else {
                replicates.iter().map(|r| r[key]).collect()
            };
            let (mean, sd) = mean_sd(&values);
            if paired {
                print!(" {:>+7.3}({:>4.3})", mean, sd);
            } else {
                print!(" {mean:>12.4}");
            }
        }
        println!();
    }
}

#[test]
#[ignore]
fn rwkv_review_order_shock() {
    let input = env::var("ANKI_RWKV_REVIEW_ORDER_INPUT").expect("set ANKI_RWKV_REVIEW_ORDER_INPUT");
    let output =
        env::var("ANKI_RWKV_REVIEW_ORDER_OUTPUT").expect("set ANKI_RWKV_REVIEW_ORDER_OUTPUT");
    assert_ne!(Path::new(&input), Path::new(&output));
    let sample_modulus: u64 = 10;
    let residues: Vec<u64> = env::var("ANKI_RWKV_REVIEW_ORDER_SAMPLE_RESIDUES")
        .unwrap_or_else(|_| "0,1,2,3,4".into())
        .split(',')
        .map(|v| v.trim().parse().unwrap())
        .collect();
    let input_bytes = fs::read(&input).unwrap();
    let mut source: Value = serde_json::from_slice(&input_bytes).unwrap();
    let rows: Vec<Row> =
        serde_json::from_value(source.as_object_mut().unwrap().remove("rows").unwrap()).unwrap();
    assert!(rows.windows(2).all(|pair| pair[0].0 < pair[1].0));
    let all_inputs = parse_inputs(source["inputs"].as_array().expect("inputs"));
    let weights = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join(MODEL_PATH);
    let mut inference = super::state_scope_bench::load_for_scopes(
        weights.clone(),
        StatePrecision::Int8,
        [false; 3],
    );
    let started = Instant::now();
    inference
        .warm_up_reviews(rows_to_reviews(&rows), false)
        .unwrap();
    println!("warm-up: {:.1}s", started.elapsed().as_secs_f64());

    let wanted = [
        "asc_r",
        "random_daily",
        "mix_2",
        "mix_4",
        "rr_deck",
        "asc_r_noise1",
        "random_daily_noise1",
    ];
    let all_strategies: Vec<Strategy> = strategies()
        .into_iter()
        .filter(|s| wanted.contains(&s.name.as_str()))
        .collect();
    assert_eq!(all_strategies.len(), wanted.len());
    let mut results = serde_json::Map::new();
    results.insert(
        "meta".into(),
        json!({
            "backup_sha256": source["backup_sha256"],
            "input_blake3": blake3::hash(&input_bytes).to_hex().to_string(),
            "residues": residues, "sample_modulus": sample_modulus,
            "truth": "shocked cards: true R = predicted R squared; grades drawn from true R",
            "grade_seconds": GRADE_SECONDS, "counterfactual": false,
        }),
    );
    let mut collected = ShockCollected::new();
    let mut replicates = serde_json::Map::new();
    let overall_started = Instant::now();
    for &residue in &residues {
        let inputs: Vec<_> = all_inputs
            .iter()
            .filter(|i| {
                (simulation_unit_hash(i.review_input.card_id, 0, 123) * 1e9) as u64 % sample_modulus
                    == residue
            })
            .cloned()
            .collect();
        println!(
            "\n######## replicate residue {residue}: {} cards",
            inputs.len()
        );
        let (deck, deck_cards) = pick_shock_deck(&inputs).expect("a deck with 30+ cards");
        let deck_mask: Vec<bool> = inputs
            .iter()
            .map(|i| i.review_input.deck_id == Some(deck))
            .collect();
        let scatter_mask: Vec<bool> = inputs
            .iter()
            .map(|i| simulation_unit_hash(i.review_input.card_id, -3, 5) < 0.10)
            .collect();
        let groups = vec![("deck", deck_mask), ("scatter", scatter_mask)];
        println!(
            "  deck_shock: deck {deck}, {deck_cards} cards ({:.1}%); scatter: {} cards",
            100.0 * deck_cards as f64 / inputs.len() as f64,
            groups[1].1.iter().filter(|&&m| m).count()
        );
        let mut replicate = serde_json::Map::new();
        replicate.insert(
            "shock_groups".into(),
            json!({
                "cards": inputs.len(), "deck_id": deck, "deck_cards": deck_cards,
                "deck_share": deck_cards as f64 / inputs.len() as f64,
                "scatter_cards": groups[1].1.iter().filter(|&&m| m).count(),
            }),
        );

        let unlimited = Scenario {
            name: "unlimited",
            days: 60,
            review_limit: usize::MAX,
            age_days: 0,
        };
        let calibration = run_strategy(
            &inference,
            &inputs,
            &unlimited,
            &all_strategies[0],
            false,
            None,
        )
        .unwrap();
        let a = f64::from(calibration.reviews_per_day.iter().sum::<u32>()) / unlimited.days as f64;
        println!("  unlimited calibration A={a:.2}");
        replicate.insert("A".into(), json!(a));
        let scenarios = [
            Scenario {
                name: "chronic_60",
                days: 90,
                review_limit: (0.6 * a).round() as usize,
                age_days: 0,
            },
            Scenario {
                name: "vacation_30",
                days: 90,
                review_limit: a.round() as usize,
                age_days: 30,
            },
        ];
        for scenario in scenarios {
            println!(
                "\n### residue {residue} {} (limit {}/day)",
                scenario.name, scenario.review_limit
            );
            let scenario_started = Instant::now();
            // (shock name, group whose truth is shocked)
            let shocks: [(&str, Option<usize>); 3] =
                [("none", None), ("deck", Some(0)), ("scatter", Some(1))];
            let jobs: Vec<(&str, Option<usize>, &Strategy)> = shocks
                .iter()
                .flat_map(|&(name, applied)| all_strategies.iter().map(move |s| (name, applied, s)))
                .collect();
            let runs: Vec<(String, String, RunResult)> = jobs
                .par_iter()
                .map(|&(shock, applied, strategy)| {
                    let spec = ShockSpec {
                        groups: groups.clone(),
                        applied,
                    };
                    let started = Instant::now();
                    let result =
                        run_strategy(&inference, &inputs, &scenario, strategy, false, Some(&spec))
                            .unwrap();
                    println!(
                        "[{residue}/{}/{shock}/{}] done in {:.0}s",
                        scenario.name,
                        strategy.name,
                        started.elapsed().as_secs_f64()
                    );
                    (shock.to_string(), strategy.name.clone(), result)
                })
                .collect();
            let mut shock_json = serde_json::Map::new();
            for (shock, strategy, run) in &runs {
                let mut summary = run.shock_summary.clone();
                if shock != "none" {
                    let control = &runs
                        .iter()
                        .find(|(s, st, _)| s == "none" && st == strategy)
                        .unwrap()
                        .2
                        .shock_summary;
                    for day in [7, 14, 30] {
                        let key = format!("{shock}.m.pred_d{day}");
                        summary
                            .insert(format!("{shock}.det_d{day}"), summary[&key] - control[&key]);
                    }
                    for (new, key) in [
                        ("spill_end_true", "u.end_true_mean"),
                        ("spill_min_lt05", "u.min_true_lt05"),
                    ] {
                        let key = format!("{shock}.{key}");
                        summary.insert(format!("{shock}.{new}"), summary[&key] - control[&key]);
                    }
                }
                collected
                    .entry((scenario.name.to_string(), shock.clone(), strategy.clone()))
                    .or_default()
                    .push(summary);
                shock_json
                    .entry(shock.clone())
                    .or_insert_with(|| json!({}))
                    .as_object_mut()
                    .unwrap()
                    .insert(strategy.clone(), run.metrics.clone());
            }
            replicate.insert(
                scenario.name.into(),
                json!({
                    "review_limit": scenario.review_limit,
                    "runtime_seconds": scenario_started.elapsed().as_secs_f64(),
                    "runs": shock_json,
                }),
            );
            replicates.insert(
                format!("residue_{residue}"),
                Value::Object(replicate.clone()),
            );
            results.insert("replicates".into(), Value::Object(replicates.clone()));
            results.insert("aggregate".into(), shock_aggregate(&collected));
            write_output(&output, &results);
        }
        println!(
            "elapsed total {:.0}s",
            overall_started.elapsed().as_secs_f64()
        );
    }

    for scenario in ["chronic_60", "vacation_30"] {
        for shock in ["deck", "scatter"] {
            let columns = shock_columns(shock);
            print_shock_table(
                &format!("{scenario} / {shock}_shock: MEAN over replicates (shocked cards unless det/spill)"),
                &collected,
                scenario,
                shock,
                &columns,
                false,
            );
            print_shock_table(
                &format!("{scenario} / {shock}_shock: PAIRED delta vs asc_r, mean(sd)"),
                &collected,
                scenario,
                shock,
                &columns,
                true,
            );
        }
        let columns: Vec<(String, String)> = [
            ("decks/day", "conc.distinct_decks_per_day"),
            ("norm_entropy", "conc.norm_entropy"),
            ("gap_p50", "conc.gap_p50"),
            ("gap_p90", "conc.gap_p90"),
            ("gap_max", "conc.gap_max"),
        ]
        .map(|(l, k)| (l.to_string(), k.to_string()))
        .to_vec();
        for shock in ["none", "deck", "scatter"] {
            print_shock_table(
                &format!("{scenario} / {shock}: deck concentration, MEAN"),
                &collected,
                scenario,
                shock,
                &columns,
                false,
            );
        }
    }
}
