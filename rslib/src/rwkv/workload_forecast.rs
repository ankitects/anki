// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Day-by-day workload forecast with confidence bands.
//!
//! Each path replays the DR workload simulation's day loop on the user's real
//! cards with its own grade seed: the RWKV curve model draws every grade, and
//! each card's own scheduler (RWKV curve, FSRS or SM-2) sets the next interval
//! at the card's desired retention. The paths only differ in their sampled
//! answers, so the bands cover answer randomness under the model, not a user
//! skipping days or doing only part of the due reviews.

use std::sync::Arc;

use fsrs::MemoryState;
use fsrs::FSRS;

use super::workload_comparison::fsrs_answer;
use super::workload_comparison::valid_memory;
use super::*;
use crate::scheduler::fsrs::simulator::normalized_fsrs_parameters;

pub(super) const FORECAST_MAX_DAYS: u32 = 365;
pub(super) const FORECAST_MAX_PATHS: u32 = 2000;

struct ForecastCard {
    card: RwkvSimulationCard,
    retention: f32,
    scheduler: RwkvForecastScheduler,
    group: usize,
    fsrs: Option<Arc<FSRS>>,
    memory: Option<MemoryState>,
}

struct ForecastLimits {
    review: Vec<usize>,
    new: Vec<usize>,
    review_today: Vec<usize>,
    new_today: Vec<usize>,
}

/// One path's daily counts, plus the running totals of the DR workload
/// simulation (for the N=1 equality check).
#[derive(Clone, Debug, Default, PartialEq)]
pub(super) struct ForecastPath {
    pub due: Vec<u32>,
    pub done: Vec<u32>,
    pub new: Vec<u32>,
    pub lapses: Vec<u32>,
    pub seconds: Vec<f32>,
    /// Per group: due, done and new counts per day.
    pub group_days: Vec<[Vec<u32>; 3]>,
    pub total_cost: f32,
    pub review_count: u32,
}

struct ForecastRun<'a> {
    cards: &'a [ForecastCard],
    base_state: &'a ReviewStateMaps,
    limits: &'a ForecastLimits,
    config: &'a RwkvWorkloadForecastConfig,
    groups: usize,
}

impl RwkvInference {
    /// Forecasts from the resident warmed state, leaving it unchanged.
    pub fn forecast_workload_from_warm_up(
        &self,
        inputs: Vec<RwkvForecastCard>,
        config: RwkvWorkloadForecastConfig,
        progress: &mut dyn FnMut(u32, u32) -> io::Result<()>,
    ) -> io::Result<RwkvWorkloadForecastOutput> {
        let (group_ids, paths, estimated) = self.forecast_paths(
            inputs,
            &self.warm_up_states,
            self.features.clone(),
            &config,
            progress,
        )?;
        Ok(summarize_forecast(
            &group_ids,
            &paths,
            config.days,
            estimated,
        ))
    }

    pub(super) fn forecast_paths(
        &self,
        inputs: Vec<RwkvForecastCard>,
        base_state: &ReviewStateMaps,
        base_features: FeatureState,
        config: &RwkvWorkloadForecastConfig,
        progress: &mut dyn FnMut(u32, u32) -> io::Result<()>,
    ) -> io::Result<(Vec<i64>, Vec<ForecastPath>, u32)> {
        if config.days == 0 || config.days > FORECAST_MAX_DAYS {
            return Err(forecast_invalid(format!(
                "forecast days must be 1..={FORECAST_MAX_DAYS}"
            )));
        }
        if config.paths == 0 || config.paths > FORECAST_MAX_PATHS {
            return Err(forecast_invalid(format!(
                "forecast paths must be 1..={FORECAST_MAX_PATHS}"
            )));
        }
        let total = config.paths;
        progress(0, total)?;
        let mut group_ids = inputs.iter().map(|card| card.group).collect::<Vec<_>>();
        group_ids.extend(config.groups.iter().map(|group| group.group));
        group_ids.sort_unstable();
        group_ids.dedup();
        let limits = forecast_limits(&group_ids, config);
        let state_len = base_state.state_len_for(&StreamIds::for_workload_inputs(
            &inputs
                .iter()
                .map(|card| card.input.clone())
                .collect::<Vec<_>>(),
        ));
        let (cards, estimated) = {
            let mut worker = self.workload_worker(base_features.clone(), HashMap::new());
            let states = SimulationStates::new(base_state);
            worker.initial_forecast_cards(inputs, &group_ids, &states)?
        };
        let run = ForecastRun {
            cards: &cards,
            base_state,
            limits: &limits,
            config,
            groups: group_ids.len(),
        };
        let simulate_path = |path: u32| {
            let mut worker = self.workload_worker(base_features.clone(), HashMap::new());
            worker.forecast_path(&run, config.seed.wrapping_add(path))
        };
        let concurrent = (WORKLOAD_TARGET_STATE_BUDGET_BYTES / state_len.max(1))
            .clamp(1, rayon::current_num_threads())
            .min(total as usize);
        let next_path = AtomicUsize::new(0);
        let stopped = AtomicBool::new(false);
        let (sender, receiver) = std::sync::mpsc::channel();
        let mut results = (0..total).map(|_| None).collect::<Vec<_>>();
        std::thread::scope(|scope| {
            let (next_path, stopped, simulate_path) = (&next_path, &stopped, &simulate_path);
            scope.spawn(move || {
                rayon::scope(|workers| {
                    for _ in 0..concurrent {
                        let sender = sender.clone();
                        workers.spawn(move |_| {
                            while !stopped.load(Ordering::Relaxed) {
                                let path = next_path.fetch_add(1, Ordering::Relaxed) as u32;
                                if path >= total {
                                    break;
                                }
                                if sender.send((path, simulate_path(path))).is_err() {
                                    break;
                                }
                            }
                        });
                    }
                });
            });
            let mut completed = 0;
            for (path, result) in receiver {
                let step = result.and_then(|record| {
                    results[path as usize] = Some(record);
                    completed += 1;
                    progress(completed, total)
                });
                if let Err(err) = step {
                    stopped.store(true, Ordering::Relaxed);
                    return Err(err);
                }
            }
            Ok(())
        })?;
        let paths = results
            .into_iter()
            .map(|path| path.expect("every forecast path completed"))
            .collect();
        Ok((group_ids, paths, estimated))
    }

    fn initial_forecast_cards(
        &mut self,
        inputs: Vec<RwkvForecastCard>,
        group_ids: &[i64],
        states: &SimulationStates<'_>,
    ) -> io::Result<(Vec<ForecastCard>, u32)> {
        let query_inputs = inputs
            .iter()
            .map(|card| simulation_query_input(&card.input, card.desired_retention, 0))
            .collect::<Vec<_>>();
        let predictions = self.workload_query_predictions_for_inputs(&query_inputs, states);
        let mut models = HashMap::<Vec<u32>, Arc<FSRS>>::new();
        let mut estimated = 0;
        let mut cards = Vec::with_capacity(inputs.len());
        for (input, prediction) in inputs.into_iter().zip(&predictions) {
            let retention = input.desired_retention;
            if !(0.0..1.0).contains(&retention) || retention <= 0.0 {
                return Err(forecast_invalid(
                    "forecast desired retention must be in (0, 1)",
                ));
            }
            let mut card = simulation_card(&input.input, prediction, retention);
            if let Some(due_day) = input.due_day {
                if !card.is_new {
                    card.due_day = due_day.max(0);
                }
            }
            let (fsrs, memory) = if input.scheduler == RwkvForecastScheduler::Fsrs {
                let params = normalized_fsrs_parameters(&input.fsrs_parameters)
                    .map_err(|err| forecast_invalid(format!("invalid FSRS parameters: {err}")))?;
                let key = params
                    .iter()
                    .map(|value| value.to_bits())
                    .collect::<Vec<_>>();
                let model = match models.get(&key) {
                    Some(model) => Arc::clone(model),
                    None => {
                        let model = Arc::new(
                            FSRS::new(&params).map_err(|err| forecast_invalid(err.to_string()))?,
                        );
                        models.insert(key, Arc::clone(&model));
                        model
                    }
                };
                let memory = if card.is_new {
                    None
                } else if let Some(memory) = input
                    .fsrs_memory
                    .map(|(stability, difficulty, stability_fast)| MemoryState {
                        stability,
                        difficulty,
                        stability_fast,
                    })
                    .filter(valid_memory)
                {
                    Some(memory)
                } else {
                    estimated += 1;
                    let historical = if (0.0..1.0).contains(&input.historical_retention) {
                        input.historical_retention
                    } else {
                        0.9
                    };
                    Some(
                        model
                            .memory_state_from_sm2(
                                card.ease_factor.max(1300) as f32 / 1000.0,
                                card.interval_days.max(1) as f32,
                                historical,
                            )
                            .map_err(|err| forecast_invalid(err.to_string()))?,
                    )
                };
                (Some(model), memory)
            } else {
                (None, None)
            };
            cards.push(ForecastCard {
                card,
                retention,
                scheduler: input.scheduler,
                group: group_ids
                    .binary_search(&input.group)
                    .expect("every card group is listed"),
                fsrs,
                memory,
            });
        }
        Ok((cards, estimated))
    }

    fn forecast_path(&mut self, run: &ForecastRun<'_>, seed: u32) -> io::Result<ForecastPath> {
        let config = run.config;
        let days = config.days as usize;
        let max_interval = config.max_interval.max(1);
        let state_update_interval = config.state_update_interval.max(1);
        let review_model = &config.review_model;
        let single_step = config.single_step_answers && state_update_interval == 1;
        let mut states = SimulationStates::new(run.base_state);
        let mut cards = run
            .cards
            .iter()
            .map(|card| ForecastCard {
                card: card.card.clone(),
                retention: card.retention,
                scheduler: card.scheduler,
                group: card.group,
                fsrs: card.fsrs.clone(),
                memory: card.memory,
            })
            .collect::<Vec<_>>();
        let mut path = ForecastPath {
            due: vec![0; days],
            done: vec![0; days],
            new: vec![0; days],
            lapses: vec![0; days],
            seconds: vec![0.0; days],
            group_days: (0..run.groups)
                .map(|_| [vec![0; days], vec![0; days], vec![0; days]])
                .collect(),
            total_cost: 0.0,
            review_count: 0,
        };
        let needs_recall_order = matches!(
            config.review_order,
            ReviewCardOrder::RetrievabilityAscending
                | ReviewCardOrder::RetrievabilityDescending
                | ReviewCardOrder::RelativeOverdueness
        );
        for day in 0..days {
            let day_i64 = day as i64;
            let mut due_reviews = cards
                .iter()
                .enumerate()
                .filter_map(|(index, card)| {
                    (!card.card.is_new && !card.card.suspended && card.card.due_day <= day_i64)
                        .then_some(index)
                })
                .collect::<Vec<_>>();
            for index in &due_reviews {
                path.due[day] += 1;
                path.group_days[cards[*index].group][0][day] += 1;
            }
            // Only the recall-based orders read the order predictions, so the
            // other orders skip those queries without changing the result.
            let prediction_by_index = if needs_recall_order {
                let order_inputs = due_reviews
                    .iter()
                    .map(|index| {
                        let card = &cards[*index];
                        simulation_query_input_for_card(&card.card, card.retention, day_i64)
                    })
                    .collect::<Vec<_>>();
                let predictions =
                    self.workload_query_predictions_for_inputs(&order_inputs, &states);
                due_reviews
                    .iter()
                    .copied()
                    .zip(predictions)
                    .collect::<HashMap<_, _>>()
            } else {
                HashMap::new()
            };
            due_reviews.sort_by_key(|index| {
                simulation_review_priority(
                    &cards[*index].card,
                    prediction_by_index.get(index),
                    config.review_order,
                    cards[*index].retention,
                )
            });
            let (review_limits, new_limits) = if day == 0 {
                (&run.limits.review_today, &run.limits.new_today)
            } else {
                (&run.limits.review, &run.limits.new)
            };
            let mut reviews_taken = vec![0usize; run.groups];
            due_reviews.retain(|index| {
                let group = cards[*index].group;
                let keep = reviews_taken[group] < review_limits[group];
                reviews_taken[group] += usize::from(keep);
                keep
            });
            let mut due_new = cards
                .iter()
                .enumerate()
                .filter_map(|(index, card)| {
                    (card.card.is_new && !card.card.suspended && card.card.due_day <= day_i64)
                        .then_some(index)
                })
                .collect::<Vec<_>>();
            due_new.sort_by_key(|index| cards[*index].card.review_input.review_input.card_id);
            let mut new_taken = vec![0usize; run.groups];
            due_new.retain(|index| {
                let group = cards[*index].group;
                let keep = new_taken[group] < new_limits[group]
                    && (config.new_cards_ignore_review_limit
                        || reviews_taken[group] + new_taken[group] < review_limits[group]);
                new_taken[group] += usize::from(keep);
                keep
            });
            let mut due_indexes = due_reviews;
            due_indexes.extend(due_new);

            let mut offset = 0;
            while offset < due_indexes.len() {
                let until_update =
                    state_update_interval - (path.review_count % state_update_interval);
                let chunk_len = (until_update as usize).min(due_indexes.len() - offset);
                let chunk = &due_indexes[offset..offset + chunk_len];
                let query_inputs = chunk
                    .iter()
                    .map(|index| {
                        let card = &cards[*index];
                        simulation_query_input_for_card(&card.card, card.retention, day_i64)
                    })
                    .collect::<Vec<_>>();
                let predictions =
                    self.workload_query_predictions_for_inputs(&query_inputs, &states);
                let eases = chunk
                    .iter()
                    .zip(&predictions)
                    .map(|(index, prediction)| {
                        let card = &cards[*index];
                        let retrievability =
                            valid_probability_or_default(prediction.retrievability, card.retention);
                        simulation_grade(
                            review_model.probabilities_for(retrievability),
                            forecast_seeded_card_id(
                                card.card.review_input.review_input.card_id,
                                seed,
                            ),
                            day_i64,
                            card.card.reps,
                        )
                    })
                    .collect::<Vec<_>>();
                let rwkv_positions = chunk
                    .iter()
                    .enumerate()
                    .filter_map(|(position, index)| {
                        (cards[*index].scheduler == RwkvForecastScheduler::Rwkv).then_some(position)
                    })
                    .collect::<Vec<_>>();
                let mut rwkv_intervals = vec![None; chunk.len()];
                if !rwkv_positions.is_empty() && !single_step {
                    let rwkv_inputs = rwkv_positions
                        .iter()
                        .map(|position| query_inputs[*position].clone())
                        .collect::<Vec<_>>();
                    let rwkv_eases = rwkv_positions
                        .iter()
                        .map(|position| eases[*position])
                        .collect::<Vec<_>>();
                    let intervals = self.selected_answer_intervals_for_inputs(
                        &rwkv_inputs,
                        &states,
                        &rwkv_eases,
                    );
                    for (position, interval) in rwkv_positions.into_iter().zip(intervals) {
                        rwkv_intervals[position] = interval;
                    }
                }

                for ((position, index), ease) in chunk.iter().enumerate().zip(&eases) {
                    let grade_seconds = review_model.grade_seconds[(*ease - 1) as usize];
                    path.total_cost += grade_seconds;
                    path.review_count += 1;
                    path.seconds[day] += grade_seconds;
                    let card = &mut cards[*index];
                    let group = card.group;
                    if card.card.is_new {
                        path.new[day] += 1;
                        path.group_days[group][2][day] += 1;
                    } else {
                        path.done[day] += 1;
                        path.group_days[group][1][day] += 1;
                        if *ease == 1 {
                            path.lapses[day] += 1;
                        }
                    }

                    let duration_millis = (grade_seconds * 1000.0).round().max(1.0) as i64;
                    let answer_input = simulation_answer_input(
                        &card.card,
                        card.retention,
                        day_i64,
                        *ease,
                        duration_millis,
                    );
                    // Single-step answers take the RWKV interval from the
                    // selected grade's curve alone, skipping the other three
                    // grades of the order check.
                    let answer_curve = if single_step
                        && card.scheduler == RwkvForecastScheduler::Rwkv
                    {
                        let interval_input = simulated_answer_input(&query_inputs[position], *ease);
                        Some(
                            self.review_heads_for_state(
                                &interval_input,
                                states.state_view(&interval_input).as_ref(),
                            )?
                            .curve,
                        )
                    } else {
                        None
                    };
                    let interval = match card.scheduler {
                        RwkvForecastScheduler::Rwkv => match &answer_curve {
                            Some(curve) => {
                                interval_for_curve(curve, card.retention, self.max_interval_days)
                                    .unwrap_or(1)
                            }
                            None => rwkv_intervals[position].unwrap_or(1),
                        },
                        RwkvForecastScheduler::Fsrs => {
                            let elapsed = (day_i64 - card.card.last_review_day).max(0) as u32;
                            let model = card.fsrs.as_ref().expect("FSRS cards carry a model");
                            let next =
                                fsrs_answer(model, card.memory, card.retention, elapsed, *ease)?;
                            card.memory = Some(next.memory);
                            next.interval.round().max(1.0) as u32
                        }
                        RwkvForecastScheduler::Sm2 => sm2_answer(&mut card.card, *ease),
                    }
                    .clamp(1, max_interval);
                    if path.review_count.is_multiple_of(state_update_interval) {
                        self.apply_simulation_answer(&answer_input, &mut states)?;
                    }

                    let card = &mut card.card;
                    card.last_review_day = day_i64;
                    card.due_day = day_i64 + interval as i64;
                    card.interval_days = interval as i64;
                    card.reps += 1;
                    if card.is_new {
                        card.is_new = false;
                        card.review_input.review_input.card_type = Some(CardType::Review as i64);
                    }
                    if *ease == 1 {
                        card.lapses += 1;
                        if config
                            .suspend_after_lapses
                            .is_some_and(|threshold| card.lapses >= threshold as i64)
                        {
                            card.suspended = true;
                        }
                    }
                }
                offset += chunk_len;
            }
        }
        Ok(path)
    }
}

/// Seed 0 keeps the DR workload simulation's grade draws.
fn forecast_seeded_card_id(card_id: i64, seed: u32) -> i64 {
    card_id ^ i64::from(seed).wrapping_mul(0x5851_F42D_4C95_7F2D)
}

/// SM-2 next interval in days, updating the card's ease factor (permille).
fn sm2_answer(card: &mut RwkvSimulationCard, ease: u8) -> u32 {
    let factor = if card.ease_factor > 0 {
        card.ease_factor
    } else {
        2500
    };
    if card.is_new {
        card.ease_factor = factor;
        return if ease == 4 { 4 } else { 1 };
    }
    let interval = card.interval_days.max(1) as f64;
    let (next, factor) = match ease {
        1 => (1.0, factor - 200),
        2 => ((interval * 1.2).max(interval + 1.0), factor - 150),
        3 => (
            (interval * factor as f64 / 1000.0).max(interval + 1.0),
            factor,
        ),
        _ => (
            (interval * factor as f64 / 1000.0 * 1.3).max(interval + 2.0),
            factor + 150,
        ),
    };
    card.ease_factor = factor.max(1300);
    next.round().clamp(1.0, u32::MAX as f64) as u32
}

fn forecast_limits(group_ids: &[i64], config: &RwkvWorkloadForecastConfig) -> ForecastLimits {
    let mut limits = ForecastLimits {
        review: vec![config.default_review_limit as usize; group_ids.len()],
        new: vec![config.default_new_limit as usize; group_ids.len()],
        review_today: vec![config.default_review_limit as usize; group_ids.len()],
        new_today: vec![config.default_new_limit as usize; group_ids.len()],
    };
    for group in &config.groups {
        let index = group_ids
            .binary_search(&group.group)
            .expect("every configured group is listed");
        limits.review[index] = group.review_limit as usize;
        limits.new[index] = group.new_limit as usize;
        limits.review_today[index] =
            group.review_limit.saturating_sub(group.reviews_done_today) as usize;
        limits.new_today[index] = group.new_limit.saturating_sub(group.new_done_today) as usize;
    }
    limits
}

/// Linear-interpolation quantile (type 7) of sorted values.
pub(super) fn forecast_quantile(sorted: &[f32], q: f32) -> f32 {
    match sorted.len() {
        0 => 0.0,
        1 => sorted[0],
        len => {
            let h = (len - 1) as f64 * q.clamp(0.0, 1.0) as f64;
            let low = h.floor() as usize;
            let high = (low + 1).min(len - 1);
            let weight = h - low as f64;
            (sorted[low] as f64 + weight * (sorted[high] as f64 - sorted[low] as f64)) as f32
        }
    }
}

pub(super) fn forecast_band(values: &mut [f32]) -> RwkvForecastBand {
    if values.is_empty() {
        return RwkvForecastBand::default();
    }
    values.sort_by(|a, b| a.total_cmp(b));
    let mean = (values.iter().map(|value| *value as f64).sum::<f64>() / values.len() as f64) as f32;
    RwkvForecastBand {
        mean,
        p1: forecast_quantile(values, 0.01),
        p5: forecast_quantile(values, 0.05),
        p50: forecast_quantile(values, 0.50),
        p95: forecast_quantile(values, 0.95),
        p99: forecast_quantile(values, 0.99),
    }
}

pub(super) fn summarize_forecast(
    group_ids: &[i64],
    paths: &[ForecastPath],
    days: u32,
    estimated_fsrs_states: u32,
) -> RwkvWorkloadForecastOutput {
    let count = paths.len().max(1) as f32;
    let band = |day: usize, metric: &dyn Fn(&ForecastPath, usize) -> f32| {
        let mut values = paths
            .iter()
            .map(|path| metric(path, day))
            .collect::<Vec<_>>();
        forecast_band(&mut values)
    };
    let days_out = (0..days as usize)
        .map(|day| RwkvForecastDay {
            day: day as u32,
            due: band(day, &|path, day| path.due[day] as f32),
            done: band(day, &|path, day| path.done[day] as f32),
            new: band(day, &|path, day| path.new[day] as f32),
            lapses: band(day, &|path, day| path.lapses[day] as f32),
            seconds: band(day, &|path, day| path.seconds[day]),
        })
        .collect();
    let groups = group_ids
        .iter()
        .enumerate()
        .map(|(group, id)| {
            let mean = |metric: usize| {
                (0..days as usize)
                    .map(|day| {
                        paths
                            .iter()
                            .map(|path| path.group_days[group][metric][day] as f32)
                            .sum::<f32>()
                            / count
                    })
                    .collect::<Vec<_>>()
            };
            RwkvForecastGroupDays {
                group: *id,
                due_mean: mean(0),
                done_mean: mean(1),
                new_mean: mean(2),
            }
        })
        .collect();
    RwkvWorkloadForecastOutput {
        paths: paths.len() as u32,
        days: days_out,
        groups,
        estimated_fsrs_states,
        scenarios: None,
    }
}

fn forecast_invalid(message: impl Into<String>) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidInput, message.into())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn quantiles_interpolate_between_sorted_values() {
        let values = (1..=101).map(|value| value as f32).collect::<Vec<_>>();
        assert_eq!(forecast_quantile(&values, 0.0), 1.0);
        assert_eq!(forecast_quantile(&values, 0.5), 51.0);
        assert_eq!(forecast_quantile(&values, 0.95), 96.0);
        assert_eq!(forecast_quantile(&values, 1.0), 101.0);
        assert!((forecast_quantile(&[0.0, 10.0], 0.25) - 2.5).abs() < 1e-6);
        assert_eq!(forecast_quantile(&[7.0], 0.99), 7.0);
        let mut unsorted = vec![5.0, 1.0, 3.0, 2.0, 4.0];
        let band = forecast_band(&mut unsorted);
        assert_eq!(band.mean, 3.0);
        assert_eq!(band.p50, 3.0);
        assert!(band.p1 <= band.p5 && band.p5 <= band.p50);
        assert!(band.p50 <= band.p95 && band.p95 <= band.p99);
    }

    #[test]
    fn seed_zero_keeps_the_dr_simulation_draws() {
        assert_eq!(forecast_seeded_card_id(12345, 0), 12345);
        assert_ne!(forecast_seeded_card_id(12345, 1), 12345);
    }

    #[test]
    fn sm2_intervals_grow_on_success_and_reset_on_lapse() {
        let mut card = RwkvSimulationCard {
            review_input: RwkvWorkloadSimulationInput {
                review_input: ReviewInput {
                    card_id: 1,
                    note_id: None,
                    deck_id: None,
                    preset_id: None,
                    is_query: true,
                    ease: None,
                    duration_millis: None,
                    card_type: Some(CardType::Review as i64),
                    day_offset: None,
                    current_elapsed_days: None,
                    current_elapsed_seconds: None,
                    target_retentions: [None; 4],
                    enforce_grade_order: true,
                },
                interval_days: Some(10),
                ease_factor: Some(2500),
                reps: Some(3),
                lapses: Some(0),
            },
            due_day: 0,
            last_review_day: -10,
            reps: 3,
            lapses: 0,
            interval_days: 10,
            ease_factor: 2500,
            is_new: false,
            suspended: false,
        };
        assert_eq!(sm2_answer(&mut card, 3), 25);
        assert_eq!(card.ease_factor, 2500);
        assert_eq!(sm2_answer(&mut card, 1), 1);
        assert_eq!(card.ease_factor, 2300);
    }

    #[test]
    fn summary_reports_bands_and_group_means() {
        let path = |due: u32| ForecastPath {
            due: vec![due, due * 2],
            done: vec![due, due],
            new: vec![0, 1],
            lapses: vec![0, 0],
            seconds: vec![due as f32 * 10.0, 0.0],
            group_days: vec![[vec![due, due * 2], vec![due, due], vec![0, 1]]],
            total_cost: 0.0,
            review_count: 0,
        };
        let paths = (0..=100).map(path).collect::<Vec<_>>();
        let output = summarize_forecast(&[7], &paths, 2, 0);
        assert_eq!(output.paths, 101);
        assert_eq!(output.days[0].due.p50, 50.0);
        assert_eq!(output.days[0].due.p95, 95.0);
        assert_eq!(output.days[1].due.p99, 198.0);
        assert_eq!(output.days[0].seconds.mean, 500.0);
        assert_eq!(output.groups[0].group, 7);
        assert_eq!(output.groups[0].new_mean, vec![0.0, 1.0]);
    }

    fn review_model() -> RwkvWorkloadReviewModel {
        RwkvWorkloadReviewModel {
            grade_seconds: [12.0, 9.0, 7.0, 5.0],
            bucket_probabilities: vec![],
        }
    }

    /// Cards answered four times at spread-out days, queried 40 days in, plus
    /// a few new cards.
    fn forecast_fixture(cards: i64) -> Option<(RwkvInference, Vec<RwkvWorkloadSimulationInput>)> {
        let path = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join(MODEL_PATH);
        if !path.exists() {
            return None;
        }
        let mut inference = RwkvInference::load(path, 0.9, 36_500).unwrap();
        let today = 40;
        let answer = |card_id: i64, day: i64, elapsed: i64, ease: u8| ReviewInput {
            card_id,
            note_id: Some(card_id),
            deck_id: Some(1 + card_id % 2),
            preset_id: Some(1),
            is_query: false,
            ease: Some(ease),
            duration_millis: Some(8000),
            card_type: Some(CardType::Review as i64),
            day_offset: Some(day),
            current_elapsed_days: Some(elapsed),
            current_elapsed_seconds: Some(elapsed * 86_400),
            target_retentions: [None; 4],
            enforce_grade_order: true,
        };
        let mut reviews = Vec::new();
        let mut inputs = Vec::new();
        for card_id in 1..=cards {
            let first = card_id % 7;
            let second = first + 1 + card_id % 3;
            let third = second + 3 + card_id % 11;
            let last = third + 2 + card_id % 13;
            let lapse = if card_id % 5 == 0 { 1 } else { 3 };
            reviews.push(answer(card_id, first, 0, 3));
            reviews.push(answer(card_id, second, second - first, lapse));
            reviews.push(answer(card_id, third, third - second, 3));
            reviews.push(answer(card_id, last, last - third, 3));
            let mut query = answer(card_id, today, today - last, 3);
            query.is_query = true;
            query.ease = None;
            query.duration_millis = None;
            inputs.push(RwkvWorkloadSimulationInput {
                review_input: query,
                interval_days: Some(last - third),
                ease_factor: Some(2500),
                reps: Some(4),
                lapses: Some(i64::from(card_id % 5 == 0)),
            });
        }
        reviews.sort_by_key(|review| (review.day_offset, review.card_id));
        inference.warm_up_reviews(reviews, false).unwrap();
        for card_id in 10_001..10_006 {
            let mut query = answer(card_id, today, 0, 3);
            query.is_query = true;
            query.ease = None;
            query.duration_millis = None;
            query.card_type = Some(CardType::New as i64);
            query.current_elapsed_days = None;
            query.current_elapsed_seconds = None;
            inputs.push(RwkvWorkloadSimulationInput {
                review_input: query,
                interval_days: None,
                ease_factor: None,
                reps: Some(0),
                lapses: Some(0),
            });
        }
        Some((inference, inputs))
    }

    fn forecast_cards(
        inputs: &[RwkvWorkloadSimulationInput],
        retention: f32,
        scheduler_for: impl Fn(i64) -> RwkvForecastScheduler,
        group_for: impl Fn(&ReviewInput) -> i64,
    ) -> Vec<RwkvForecastCard> {
        inputs
            .iter()
            .map(|input| RwkvForecastCard {
                input: input.clone(),
                due_day: None,
                group: group_for(&input.review_input),
                desired_retention: retention,
                scheduler: scheduler_for(input.review_input.card_id),
                fsrs_memory: None,
                fsrs_parameters: vec![],
                historical_retention: 0.9,
            })
            .collect()
    }

    fn forecast_config(
        days: u32,
        paths: u32,
        seed: u32,
        order: ReviewCardOrder,
    ) -> RwkvWorkloadForecastConfig {
        RwkvWorkloadForecastConfig {
            days,
            paths,
            seed,
            groups: vec![],
            default_review_limit: 25,
            default_new_limit: 2,
            new_cards_ignore_review_limit: false,
            max_interval: 36_500,
            review_order: order,
            suspend_after_lapses: None,
            state_update_interval: 1,
            single_step_answers: false,
            review_model: review_model(),
        }
    }

    #[test]
    fn one_path_with_seed_zero_matches_the_dr_workload_simulation() {
        let Some((inference, inputs)) = forecast_fixture(40) else {
            return;
        };
        for order in [
            ReviewCardOrder::RetrievabilityAscending,
            ReviewCardOrder::Day,
        ] {
            let simulated = inference
                .simulate_workload_from_warm_up(
                    inputs.clone(),
                    RwkvWorkloadSimulationConfig {
                        min_dr: 85,
                        max_dr: 85,
                        target_dr_step: 1,
                        days_to_simulate: 30,
                        review_limit: 25,
                        new_limit: 2,
                        new_cards_ignore_review_limit: false,
                        max_interval: 36_500,
                        review_order: order,
                        suspend_after_lapses: None,
                        state_update_interval: 1,
                        review_model: review_model(),
                    },
                    &mut |_, _| Ok(()),
                )
                .unwrap();
            let point = &simulated.points[0].1;
            let (_, paths, _) = inference
                .forecast_paths(
                    forecast_cards(&inputs, 0.85, |_| RwkvForecastScheduler::Rwkv, |_| 0),
                    &inference.warm_up_states,
                    inference.features.clone(),
                    &forecast_config(30, 1, 0, order),
                    &mut |_, _| Ok(()),
                )
                .unwrap();
            let path = &paths[0];
            assert!(point.review_count > 0);
            assert_eq!(path.review_count, point.review_count);
            assert_eq!(path.total_cost.to_bits(), point.cost.to_bits());
            let daily = path.done.iter().chain(&path.new).sum::<u32>();
            assert_eq!(daily, point.review_count);
        }
    }

    #[test]
    fn forecasts_are_deterministic_per_seed_and_bands_widen() {
        let Some((inference, inputs)) = forecast_fixture(120) else {
            return;
        };
        let run = |seed: u32| {
            let cards = forecast_cards(
                &inputs,
                0.9,
                |card_id| match card_id % 3 {
                    0 => RwkvForecastScheduler::Fsrs,
                    1 => RwkvForecastScheduler::Sm2,
                    _ => RwkvForecastScheduler::Rwkv,
                },
                |input| input.deck_id.unwrap_or(0),
            );
            let mut config = forecast_config(30, 40, seed, ReviewCardOrder::Day);
            config.default_review_limit = 9_999;
            config.groups = vec![RwkvForecastGroup {
                group: 1,
                review_limit: 9_999,
                new_limit: 1,
                reviews_done_today: 0,
                new_done_today: 1,
            }];
            inference
                .forecast_workload_from_warm_up(cards, config, &mut |_, _| Ok(()))
                .unwrap()
        };
        let first = run(7);
        assert_eq!(first, run(7));
        assert_ne!(first.days, run(8).days);
        assert!(first.estimated_fsrs_states > 0);
        assert_eq!(first.groups.len(), 2);
        // Group 1 already used up today's new cards.
        assert_eq!(first.groups[0].group, 1);
        assert_eq!(first.groups[0].new_mean[0], 0.0);

        let spread = |band: &RwkvForecastBand| band.p95 - band.p5;
        let day0 = &first.days[0];
        // The cards due today are fixed: only their answers vary.
        assert_eq!(spread(&day0.due), 0.0);
        assert_eq!(spread(&day0.done), 0.0);
        let early = spread(&first.days[1].due);
        let late = first.days[20..]
            .iter()
            .map(|day| spread(&day.due))
            .sum::<f32>()
            / 10.0;
        assert!(late > early, "late spread {late} <= day-1 spread {early}");
        for day in &first.days {
            for band in [&day.due, &day.done, &day.new, &day.lapses, &day.seconds] {
                assert!(band.p1 <= band.p5 && band.p5 <= band.p50);
                assert!(band.p50 <= band.p95 && band.p95 <= band.p99);
            }
        }
    }

    #[test]
    fn single_step_answers_stay_close_to_the_exact_forecast() {
        let Some((inference, inputs)) = forecast_fixture(80) else {
            return;
        };
        let run = |single_step_answers: bool| {
            let mut config = forecast_config(30, 8, 3, ReviewCardOrder::Day);
            config.default_review_limit = 9_999;
            config.single_step_answers = single_step_answers;
            inference
                .forecast_workload_from_warm_up(
                    forecast_cards(&inputs, 0.9, |_| RwkvForecastScheduler::Rwkv, |_| 0),
                    config,
                    &mut |_, _| Ok(()),
                )
                .unwrap()
        };
        let total = |output: &RwkvWorkloadForecastOutput| {
            output.days.iter().map(|day| day.done.mean).sum::<f32>()
        };
        let (exact, fast) = (run(false), run(true));
        assert_eq!(exact.days[0].due, fast.days[0].due);
        let ratio = total(&fast) / total(&exact);
        assert!(
            (0.8..1.25).contains(&ratio),
            "single-step/exact reviews {ratio}"
        );
    }
}
