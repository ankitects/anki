// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Compare two scheduling policies against the same simulated learner.
//!
//! Each run maintains its own learner state. Each scheduler sees only its own
//! answers, and paired runs use the same per-card/repetition random quantiles
//! even if the schedulers review that card on different days.

use std::sync::Arc;

use fsrs::MemoryState;
use fsrs::FSRS;

use super::*;
use crate::scheduler::fsrs::simulator::normalized_fsrs_parameters;

struct PreparedCard {
    input: RwkvComparisonCard,
    model: Arc<FSRS>,
    memory: Option<MemoryState>,
}

struct ComparisonCard {
    card: RwkvSimulationCard,
    model: Arc<FSRS>,
    scheduler_memory: Option<MemoryState>,
    learner_memory: Option<MemoryState>,
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum Scheduler {
    Fsrs,
    Rwkv,
}

#[derive(Clone, Copy)]
struct ComparisonJob {
    dr: u32,
    seed: u32,
    scheduler: Scheduler,
}

enum ComparisonEvent {
    Progress,
    Finished(usize, io::Result<ComparisonPoint>),
}

struct ComparisonPoint {
    scores: [(f32, f32); 2],
    cost: f32,
    review_count: u32,
}

const BACKGROUND_PATTERN_DAYS: usize = 365;

// The estimate covers copies owned by concurrent runs, not the resident model.
const COMPARISON_WORKER_MEMORY_BUDGET: usize = 256 * 1024 * 1024;
const COMPARISON_MAX_WORKERS: usize = 4;

struct ComparisonBackground<'a> {
    by_day: Vec<Vec<&'a RwkvComparisonBackgroundReview>>,
}

impl<'a> ComparisonBackground<'a> {
    fn new(
        reviews: &'a [RwkvComparisonBackgroundReview],
        prepared: &[PreparedCard],
    ) -> io::Result<Self> {
        let sampled: HashSet<_> = prepared
            .iter()
            .map(|card| card.input.input.review_input.card_id)
            .collect();
        let mut by_day = vec![Vec::new(); BACKGROUND_PATTERN_DAYS];
        for review in reviews {
            if sampled.contains(&review.input.card_id) {
                // The simulated answer is the only answer for sampled cards.
                continue;
            }
            let Some(day) = by_day.get_mut(review.pattern_day as usize) else {
                return Err(invalid("invalid background review day"));
            };
            if review.second_of_day >= 86_400
                || review.last_review_day > 0
                || review.input.is_query
                || !matches!(review.input.ease, Some(1..=4))
                || review.input.day_offset.is_none()
            {
                return Err(invalid("invalid background review"));
            }
            day.push(review);
        }
        Ok(Self { by_day })
    }

    fn for_day(&self, day: i64) -> &[&'a RwkvComparisonBackgroundReview] {
        &self.by_day[day as usize % BACKGROUND_PATTERN_DAYS]
    }
}

#[derive(Default)]
struct BackgroundReplay {
    last_reviews: HashMap<i64, (i64, i64)>,
}

impl BackgroundReplay {
    fn replay_until(
        &mut self,
        reviews: &[&RwkvComparisonBackgroundReview],
        day: i64,
        end: usize,
        cursor: &mut usize,
        worker: &mut RwkvInference,
        states: &mut SimulationStates<'_>,
    ) -> io::Result<()> {
        let mut answers = Vec::with_capacity(end - *cursor);
        for review in &reviews[*cursor..end] {
            let mut answer = review.input.clone();
            let previous = self
                .last_reviews
                .entry(answer.card_id)
                .or_insert((review.last_review_day, review.last_review_second));
            let second = day * 86_400 + i64::from(review.second_of_day);
            answer.day_offset = answer.day_offset.map(|today| today + day);
            answer.current_elapsed_days = Some((day - previous.0).max(0));
            answer.current_elapsed_seconds = Some((second - previous.1).max(0));
            // All replayed cards already exist in the starting state. Repeating
            // an old learning-start event must not represent a newly created card.
            if answer.card_type == Some(0) {
                answer.card_type = Some(CardType::Review as i64);
            }
            *previous = (day, second);
            answers.push(answer);
        }
        if answers.len() < 16 {
            for answer in &answers {
                worker.apply_simulation_answer(answer, states)?;
            }
        } else {
            replay_background_batch(&answers, worker, states);
        }
        *cursor = end;
        Ok(())
    }
}

fn replay_background_batch(
    answers: &[ReviewInput],
    worker: &mut RwkvInference,
    states: &mut SimulationStates<'_>,
) {
    fn import(
        own: &mut HashMap<i64, ModuleState>,
        base: &HashMap<i64, ModuleState>,
        id: Option<i64>,
    ) {
        if let Some(id) = id {
            if let Some(state) = base.get(&id) {
                own.entry(id).or_insert_with(|| state.clone());
            }
        }
    }
    fn import_shared(
        own: &mut HashMap<i64, ModuleState>,
        base: &SharedStateMap,
        id: Option<i64>,
    ) {
        if let Some(id) = id {
            if let Some(state) = base.state(&id) {
                own.entry(id).or_insert_with(|| state.into_owned());
            }
        }
    }
    for input in answers {
        import_shared(&mut states.card, &states.base.card, Some(input.card_id));
        import_shared(&mut states.note, &states.base.note, input.note_id);
        import(&mut states.deck, &states.base.deck, input.deck_id);
        import(&mut states.preset, &states.base.preset, input.preset_id);
    }
    let mut replay_states = ReviewStateMaps {
        card: std::mem::take(&mut states.card).into_iter().collect(),
        note: std::mem::take(&mut states.note).into_iter().collect(),
        deck: std::mem::take(&mut states.deck),
        preset: std::mem::take(&mut states.preset),
        global: states.global.take().or_else(|| states.base.global.clone()),
        ..Default::default()
    };
    bulk::replay_simulation_background(worker, &mut replay_states, answers);
    fn unshare(mut map: SharedStateMap) -> HashMap<i64, ModuleState> {
        map.ids()
            .into_iter()
            .filter_map(|id| map.take(&id).map(|state| (id, state)))
            .collect()
    }
    states.card = unshare(replay_states.card);
    states.note = unshare(replay_states.note);
    states.deck = replay_states.deck;
    states.preset = replay_states.preset;
    states.global = replay_states.global;
}

impl RwkvInference {
    pub fn compare_workloads_from_warm_up(
        &self,
        inputs: Vec<RwkvComparisonCard>,
        config: RwkvComparisonConfig,
        progress: &mut dyn FnMut(u32, u32) -> io::Result<()>,
    ) -> io::Result<RwkvComparisonOutput> {
        if config.min_dr > config.max_dr || config.max_dr > 99 || config.min_dr < 1 {
            return Err(invalid("invalid comparison retention range"));
        }
        if config.repetitions == 0 || config.repetitions > 100 {
            return Err(invalid("comparison repetitions must be between 1 and 100"));
        }
        if !config.review_limit.is_finite()
            || config.review_limit < 0.0
            || !config.new_limit.is_finite()
            || config.new_limit < 0.0
            || !config.output_scale.is_finite()
            || config.output_scale < 1.0
        {
            return Err(invalid("invalid comparison daily limit"));
        }

        let (prepared, estimated_fsrs_states) = prepare_cards(inputs)?;
        let targets = target_drs(config.min_dr, config.max_dr, config.target_dr_step.max(1));
        let total = 1 + targets.len() as u32 * config.repetitions * 2;
        progress(0, total)?;
        let background = ComparisonBackground::new(&config.background, &prepared)?;
        let baseline = self
            .comparison_reviewless(&prepared, &config, &background, &mut || progress(0, total))?;
        progress(1, total)?;
        let points =
            self.compare_targets(&prepared, &config, &background, &targets, total, progress)?;
        let mut output_points: [[Vec<(u32, RwkvWorkloadSimulationPoint)>; 2]; 2] =
            std::array::from_fn(|_| std::array::from_fn(|_| Vec::with_capacity(targets.len())));
        let mut points = points.into_iter();
        for dr in targets {
            let mut sums = [[(0.0f64, 0.0f64, 0.0f64, 0u64); 2]; 2];
            for _ in 0..config.repetitions {
                for scheduler_sums in &mut sums {
                    let point = points.next().expect("every comparison run completed");
                    for (sum, (memorized, weighted)) in scheduler_sums.iter_mut().zip(point.scores)
                    {
                        sum.0 += f64::from(memorized);
                        sum.1 += f64::from(weighted);
                        sum.2 += f64::from(point.cost);
                        sum.3 += u64::from(point.review_count);
                    }
                }
            }
            let average = |(memorized, weighted, cost, count): (f64, f64, f64, u64)| {
                let n = f64::from(config.repetitions);
                RwkvWorkloadSimulationPoint {
                    memorized: (memorized / n * f64::from(config.output_scale)) as f32,
                    weighted_memorized: (weighted / n * f64::from(config.output_scale)) as f32,
                    cost: (cost / n * f64::from(config.output_scale)) as f32,
                    review_count: ((count as f64 / n * f64::from(config.output_scale))
                        .round()
                        .min(f64::from(u32::MAX))) as u32,
                }
            };
            for (scheduler, scheduler_sums) in sums.into_iter().enumerate() {
                for (scorer, sum) in scheduler_sums.into_iter().enumerate() {
                    output_points[scorer][scheduler].push((dr, average(sum)));
                }
            }
        }
        let output = |points, scorer: usize| RwkvWorkloadSimulationOutput {
            reviewless_end_memorized: baseline[scorer].0 * config.output_scale,
            reviewless_end_weighted_memorized: baseline[scorer].1 * config.output_scale,
            points,
        };
        Ok(RwkvComparisonOutput {
            fsrs_scored_by_fsrs: output(std::mem::take(&mut output_points[0][0]), 0),
            rwkv_scored_by_fsrs: output(std::mem::take(&mut output_points[0][1]), 0),
            fsrs_scored_by_rwkv: output(std::mem::take(&mut output_points[1][0]), 1),
            rwkv_scored_by_rwkv: output(std::mem::take(&mut output_points[1][1]), 1),
            estimated_fsrs_states,
        })
    }

    fn compare_targets(
        &self,
        prepared: &[PreparedCard],
        config: &RwkvComparisonConfig,
        background: &ComparisonBackground<'_>,
        targets: &[u32],
        total: u32,
        progress: &mut dyn FnMut(u32, u32) -> io::Result<()>,
    ) -> io::Result<Vec<ComparisonPoint>> {
        let mut jobs = Vec::with_capacity((total - 1) as usize);
        for (target_index, &dr) in targets.iter().enumerate() {
            for seed in 0..config.repetitions {
                for (scheduler_index, scheduler) in
                    [Scheduler::Fsrs, Scheduler::Rwkv].into_iter().enumerate()
                {
                    let index = (target_index * config.repetitions as usize + seed as usize) * 2
                        + scheduler_index;
                    jobs.push((
                        index,
                        ComparisonJob {
                            dr,
                            seed,
                            scheduler,
                        },
                    ));
                }
            }
        }
        // High-retention runs generally take longest; start them first while
        // storing results in their original order for deterministic averages.
        jobs.sort_by_key(|(_, job)| std::cmp::Reverse(job.dr));
        let worker_bytes = estimated_comparison_worker_bytes(prepared, background, &self.features);
        let workers =
            comparison_worker_count(worker_bytes, jobs.len(), rayon::current_num_threads());
        let next_job = AtomicUsize::new(0);
        let stopped = AtomicBool::new(false);
        let progress_pending = AtomicBool::new(false);
        let (sender, receiver) = std::sync::mpsc::channel();
        let mut points = (0..jobs.len()).map(|_| None).collect::<Vec<_>>();
        std::thread::scope(|scope| {
            let (jobs, next_job, stopped, progress_pending) =
                (&jobs, &next_job, &stopped, &progress_pending);
            scope.spawn(move || {
                rayon::scope(|scope| {
                    for _ in 0..workers {
                        let sender = sender.clone();
                        scope.spawn(move |_| {
                            while !stopped.load(Ordering::Relaxed) {
                                let Some(&(index, job)) =
                                    jobs.get(next_job.fetch_add(1, Ordering::Relaxed))
                                else {
                                    break;
                                };
                                let result = self.compare_target(
                                    prepared,
                                    config,
                                    background,
                                    job,
                                    &mut || {
                                        if stopped.load(Ordering::Relaxed) {
                                            return Err(io::Error::new(
                                                io::ErrorKind::Interrupted,
                                                "comparison cancelled",
                                            ));
                                        }
                                        // At most one progress check needs to
                                        // wait for the calling thread.
                                        if !progress_pending.swap(true, Ordering::AcqRel) {
                                            sender.send(ComparisonEvent::Progress).map_err(
                                                |_| {
                                                    io::Error::new(
                                                        io::ErrorKind::Interrupted,
                                                        "comparison cancelled",
                                                    )
                                                },
                                            )?;
                                        }
                                        Ok(())
                                    },
                                );
                                if sender
                                    .send(ComparisonEvent::Finished(index, result))
                                    .is_err()
                                {
                                    break;
                                }
                            }
                        });
                    }
                });
            });
            let mut completed = 1;
            for event in receiver {
                let result = match event {
                    ComparisonEvent::Progress => {
                        progress_pending.store(false, Ordering::Release);
                        progress(completed, total)
                    }
                    ComparisonEvent::Finished(index, result) => result.and_then(|point| {
                        points[index] = Some(point);
                        completed += 1;
                        progress(completed, total)
                    }),
                };
                if let Err(err) = result {
                    stopped.store(true, Ordering::Relaxed);
                    return Err(err);
                }
            }
            Ok(())
        })?;
        Ok(points
            .into_iter()
            .map(|point| point.expect("every comparison run completed"))
            .collect())
    }

    fn comparison_reviewless(
        &self,
        prepared: &[PreparedCard],
        config: &RwkvComparisonConfig,
        background: &ComparisonBackground<'_>,
        check_progress: &mut dyn FnMut() -> io::Result<()>,
    ) -> io::Result<[(f32, f32); 2]> {
        let cards = initial_cards(prepared);
        let mut worker = self.workload_worker(self.features.clone(), HashMap::new());
        let mut states = SimulationStates::new(&self.warm_up_states);
        let mut replay = BackgroundReplay::default();
        for day in 0..i64::from(config.days_to_simulate) {
            if day % 7 == 0 {
                check_progress()?;
            }
            let reviews = background.for_day(day);
            replay.replay_until(
                reviews,
                day,
                reviews.len(),
                &mut 0,
                &mut worker,
                &mut states,
            )?;
        }
        comparison_memorized(
            &cards,
            &mut worker,
            &states,
            config,
            config.days_to_simulate as i64,
        )
    }

    fn compare_target(
        &self,
        prepared: &[PreparedCard],
        config: &RwkvComparisonConfig,
        background: &ComparisonBackground<'_>,
        job: ComparisonJob,
        check_progress: &mut dyn FnMut() -> io::Result<()>,
    ) -> io::Result<ComparisonPoint> {
        let ComparisonJob {
            dr,
            seed,
            scheduler,
        } = job;
        let retention = dr as f32 / 100.0;
        let mut cards = initial_cards(prepared);
        // If both roles use RWKV, their state starts equal and receives the
        // same answers. Queries do not advance it, so one copy serves both.
        let mut worker = self.workload_worker(self.features.clone(), HashMap::new());
        let mut states = SimulationStates::new(&self.warm_up_states);
        let mut background_replay = BackgroundReplay::default();
        let mut cost = 0.0;
        let mut review_count = 0u32;

        for day in 0..i64::from(config.days_to_simulate) {
            if day % 7 == 0 {
                check_progress()?;
            }
            let mut due = cards
                .iter()
                .enumerate()
                .filter_map(|(index, card)| {
                    (!card.card.is_new && !card.card.suspended && card.card.due_day <= day)
                        .then_some(index)
                })
                .collect::<Vec<_>>();
            let by_index = if matches!(
                config.review_order,
                ReviewCardOrder::RetrievabilityAscending
                    | ReviewCardOrder::RetrievabilityDescending
                    | ReviewCardOrder::RelativeOverdueness
            ) {
                let predictions = scheduler_predictions(
                    &cards,
                    &due,
                    &mut worker,
                    &states,
                    retention,
                    day,
                    scheduler,
                );
                due.iter()
                    .copied()
                    .zip(predictions)
                    .collect::<HashMap<_, _>>()
            } else {
                HashMap::new()
            };
            due.sort_by_key(|index| {
                simulation_review_priority(
                    &cards[*index].card,
                    by_index.get(index),
                    config.review_order,
                    retention,
                )
            });
            let review_limit = day_budget(config.review_limit, day);
            due.truncate(review_limit);

            let mut new = cards
                .iter()
                .enumerate()
                .filter_map(|(index, card)| {
                    (card.card.is_new && !card.card.suspended && card.card.due_day <= day)
                        .then_some(index)
                })
                .collect::<Vec<_>>();
            new.sort_by_key(|index| cards[*index].card.review_input.review_input.card_id);
            new.truncate(day_budget(config.new_limit, day));
            if !config.new_cards_ignore_review_limit {
                new.truncate(review_limit.saturating_sub(due.len()));
            }
            due.extend(new);

            let background_reviews = background.for_day(day);
            let mut background_cursor = 0;
            let due_count = due.len();
            for (ordinal, index) in due.into_iter().enumerate() {
                let background_end = (ordinal + 1) * background_reviews.len() / (due_count + 1);
                background_replay.replay_until(
                    background_reviews,
                    day,
                    background_end,
                    &mut background_cursor,
                    &mut worker,
                    &mut states,
                )?;
                let card = &mut cards[index];
                let elapsed = (day - card.card.last_review_day).max(0) as u32;
                let query = simulation_query_input_for_card(&card.card, retention, day);
                let learner_r = if card.card.is_new {
                    // The first grade is shared by both policies. Its simple
                    // baseline is intentionally distinct from model forecasts.
                    0.85
                } else {
                    learner_retrievability(card, &mut worker, &states, config, &query)?
                };
                let actual_r = performance_probability(learner_r, day, config);
                let ease = paired_grade(
                    &config.review_model,
                    actual_r,
                    card.card.review_input.review_input.card_id,
                    card.card.reps,
                    seed,
                );
                let seconds = config.review_model.grade_seconds[(ease - 1) as usize];
                cost += seconds;
                review_count = review_count.saturating_add(1);

                let interval = match scheduler {
                    Scheduler::Fsrs => {
                        let next = fsrs_answer(
                            &card.model,
                            card.scheduler_memory,
                            retention,
                            elapsed,
                            ease,
                        )?;
                        card.scheduler_memory = Some(next.memory);
                        clamped_interval_days(next.interval, config.max_interval.max(1))
                    }
                    Scheduler::Rwkv => worker.selected_answer_intervals_for_inputs(
                        std::slice::from_ref(&query),
                        &states,
                        &[ease],
                    )[0]
                    .unwrap_or(1)
                    .clamp(1, config.max_interval.max(1)),
                };

                let answer = simulation_answer_input(
                    &card.card,
                    retention,
                    day,
                    ease,
                    (seconds * 1000.0).round().max(1.0) as i64,
                );
                worker.apply_simulation_answer(&answer, &mut states)?;
                // Both scorers see this exact answer, regardless of which
                // learner generated it or which scheduler chose the interval.
                card.learner_memory = if scheduler == Scheduler::Fsrs {
                    card.scheduler_memory
                } else {
                    Some(
                        fsrs_answer(&card.model, card.learner_memory, retention, elapsed, ease)?
                            .memory,
                    )
                };
                card.card.last_review_day = day;
                card.card.due_day = day + i64::from(interval);
                card.card.interval_days = i64::from(interval);
                card.card.reps += 1;
                if card.card.is_new {
                    card.card.is_new = false;
                    card.card.review_input.review_input.card_type = Some(CardType::Review as i64);
                }
                if ease == 1 {
                    card.card.lapses += 1;
                    if config
                        .suspend_after_lapses
                        .is_some_and(|n| card.card.lapses >= i64::from(n))
                    {
                        card.card.suspended = true;
                    }
                }
            }
            background_replay.replay_until(
                background_reviews,
                day,
                background_reviews.len(),
                &mut background_cursor,
                &mut worker,
                &mut states,
            )?;
        }
        let scores = comparison_memorized(
            &cards,
            &mut worker,
            &states,
            config,
            i64::from(config.days_to_simulate),
        )?;
        Ok(ComparisonPoint {
            scores,
            cost,
            review_count,
        })
    }
}

fn estimated_comparison_worker_bytes(
    prepared: &[PreparedCard],
    background: &ComparisonBackground<'_>,
    features: &FeatureState,
) -> usize {
    let mut ids = StreamIds::default();
    for card in prepared {
        let input = &card.input.input.review_input;
        ids.card.insert(input.card_id);
        ids.note.extend(input.note_id);
        ids.deck.extend(input.deck_id);
        ids.preset.extend(input.preset_id);
    }
    for review in background.by_day.iter().flatten() {
        let input = &review.input;
        ids.card.insert(input.card_id);
        ids.note.extend(input.note_id);
        ids.deck.extend(input.deck_id);
        ids.preset.extend(input.preset_id);
    }
    // Assume full-precision state even if the resident state is compressed or
    // absent. New cards can create streams during the simulation.
    let layers = ids
        .card
        .len()
        .saturating_mul(MODULE_LAYERS[0])
        .saturating_add(ids.deck.len().saturating_mul(MODULE_LAYERS[1]))
        .saturating_add(ids.note.len().saturating_mul(MODULE_LAYERS[2]))
        .saturating_add(ids.preset.len().saturating_mul(MODULE_LAYERS[3]))
        .saturating_add(MODULE_LAYERS[4]);
    let bytes_per_layer = 4 * (HEADS * HEAD_SIZE * HEAD_SIZE + 2 * D_MODEL) + 512;
    features
        .cache_state_len()
        .saturating_mul(2) // Hash map and allocation overhead
        .saturating_add(layers.saturating_mul(bytes_per_layer))
        .saturating_add(prepared.len().saturating_mul(1024))
}

fn comparison_worker_count(worker_bytes: usize, jobs: usize, threads: usize) -> usize {
    (COMPARISON_WORKER_MEMORY_BUDGET / worker_bytes.max(1))
        .clamp(1, COMPARISON_MAX_WORKERS.min(threads.max(1)))
        .min(jobs.max(1))
}

fn invalid(message: impl Into<String>) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidInput, message.into())
}

fn prepare_cards(inputs: Vec<RwkvComparisonCard>) -> io::Result<(Vec<PreparedCard>, u32)> {
    let mut models = HashMap::<Vec<u32>, Arc<FSRS>>::new();
    let mut estimated = 0;
    let mut cards = Vec::with_capacity(inputs.len());
    for input in inputs {
        let params = normalized_fsrs_parameters(&input.fsrs_parameters)
            .map_err(|err| invalid(format!("invalid FSRS comparison parameters: {err}")))?;
        let key = params
            .iter()
            .map(|value| value.to_bits())
            .collect::<Vec<_>>();
        let model = if let Some(model) = models.get(&key) {
            Arc::clone(model)
        } else {
            let model = Arc::new(FSRS::new(&params).map_err(|err| invalid(err.to_string()))?);
            models.insert(key, Arc::clone(&model));
            model
        };
        let is_new = input.input.review_input.card_type == Some(CardType::New as i64);
        let memory = if is_new {
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
            let historical_retention = if (0.0..1.0).contains(&input.historical_retention) {
                input.historical_retention
            } else {
                0.9
            };
            Some(
                model
                    .memory_state_from_sm2(
                        input.input.ease_factor.unwrap_or(2500) as f32 / 1000.0,
                        input.input.interval_days.unwrap_or(1).max(1) as f32,
                        historical_retention,
                    )
                    .map_err(|err| invalid(format!("unable to estimate FSRS state: {err}")))?,
            )
        };
        cards.push(PreparedCard {
            input,
            model,
            memory,
        });
    }
    Ok((cards, estimated))
}

pub(super) fn valid_memory(memory: &MemoryState) -> bool {
    memory.stability.is_finite()
        && memory.stability > 0.0
        && memory.stability_fast.is_finite()
        && memory.stability_fast > 0.0
        && memory.difficulty.is_finite()
        && (1.0..=10.0).contains(&memory.difficulty)
}

fn initial_cards(prepared: &[PreparedCard]) -> Vec<ComparisonCard> {
    prepared
        .iter()
        .map(|item| {
            let input = &item.input.input;
            let is_new = input.review_input.card_type == Some(CardType::New as i64);
            ComparisonCard {
                card: RwkvSimulationCard {
                    review_input: input.clone(),
                    due_day: if is_new { 0 } else { item.input.due_day },
                    last_review_day: -input_elapsed_days(&input.review_input),
                    reps: input.reps.unwrap_or(0),
                    lapses: input.lapses.unwrap_or(0),
                    interval_days: input.interval_days.unwrap_or(1).max(1),
                    ease_factor: input.ease_factor.unwrap_or(0),
                    is_new,
                    suspended: false,
                },
                model: Arc::clone(&item.model),
                scheduler_memory: item.memory,
                learner_memory: item.memory,
            }
        })
        .collect()
}

fn scheduler_predictions(
    cards: &[ComparisonCard],
    indexes: &[usize],
    worker: &mut RwkvInference,
    states: &SimulationStates<'_>,
    retention: f32,
    day: i64,
    scheduler: Scheduler,
) -> Vec<RwkvWorkloadQueryPrediction> {
    if scheduler == Scheduler::Rwkv {
        let queries = indexes
            .iter()
            .map(|index| simulation_query_input_for_card(&cards[*index].card, retention, day))
            .collect::<Vec<_>>();
        let views = queries
            .iter()
            .map(|query| states.state_view(query))
            .collect::<Vec<_>>();
        let work_items = queries
            .iter()
            .zip(&views)
            .map(|(query, view)| ReviewPredictionBorrowedWorkItem {
                features: worker.features.features_for(query),
                state: view.as_ref(),
            })
            .collect::<Vec<_>>();
        worker
            .model
            .review_many_borrowed(&work_items, |_, heads| RwkvWorkloadQueryPrediction {
                retrievability: heads.retrievability,
                current_interval: None,
                current_s90: None,
            })
    } else {
        indexes
            .iter()
            .map(|index| {
                let card = &cards[*index];
                let elapsed = (day - card.card.last_review_day).max(0) as f32;
                RwkvWorkloadQueryPrediction {
                    retrievability: card.scheduler_memory.map_or(0.85, |memory| {
                        card.model.current_retrievability(memory, elapsed)
                    }),
                    current_interval: None,
                    current_s90: None,
                }
            })
            .collect()
    }
}

fn learner_retrievability(
    card: &ComparisonCard,
    worker: &mut RwkvInference,
    states: &SimulationStates<'_>,
    config: &RwkvComparisonConfig,
    query: &ReviewInput,
) -> io::Result<f32> {
    let result = match config.learner {
        RwkvComparisonLearner::Fsrs => {
            let memory = card
                .learner_memory
                .ok_or_else(|| invalid("missing FSRS learner state"))?;
            card.model
                .current_retrievability(memory, input_elapsed_days(query) as f32)
        }
        RwkvComparisonLearner::Rwkv => {
            // The per-answer draw needs recall only; the final S90 weight is
            // calculated separately when the memorized total is computed.
            worker
                .review_heads_for_state(query, states.state_view(query).as_ref())?
                .retrievability
        }
    };
    if valid_probability(result) {
        Ok(result)
    } else {
        Err(invalid("learner produced an invalid recall probability"))
    }
}

fn comparison_memorized(
    cards: &[ComparisonCard],
    worker: &mut RwkvInference,
    states: &SimulationStates<'_>,
    config: &RwkvComparisonConfig,
    day: i64,
) -> io::Result<[(f32, f32); 2]> {
    let mut totals = [(0.0, 0.0); 2];
    let active = cards
        .iter()
        .filter(|card| !card.card.is_new)
        .collect::<Vec<_>>();
    let queries = active
        .iter()
        .map(|card| simulation_query_input_for_card(&card.card, S90_TARGET_RETENTION, day))
        .collect::<Vec<_>>();
    let rwkv_predictions = worker.workload_query_predictions_for_inputs(&queries, states);
    for ((card, query), prediction) in active.iter().zip(&queries).zip(rwkv_predictions) {
        let memory = card
            .learner_memory
            .ok_or_else(|| invalid("missing FSRS scoring state"))?;
        let scores = [
            (
                card.model
                    .current_retrievability(memory, input_elapsed_days(query) as f32),
                Some(card.model.s90(memory)),
            ),
            (prediction.retrievability, prediction.current_s90),
        ];
        for (total, (r, s90)) in totals.iter_mut().zip(scores) {
            if !valid_probability(r) {
                return Err(invalid("scorer produced an invalid recall probability"));
            }
            let r = performance_probability(r, day, config);
            total.0 += r;
            total.1 += r * s90_weight(s90);
        }
    }
    Ok(totals)
}

pub(super) fn fsrs_answer(
    model: &FSRS,
    memory: Option<MemoryState>,
    retention: f32,
    elapsed: u32,
    ease: u8,
) -> io::Result<fsrs::ItemState> {
    let next = model
        .next_states(memory, retention, elapsed)
        .map_err(|err| invalid(err.to_string()))?;
    Ok(match ease {
        1 => next.again,
        2 => next.hard,
        3 => next.good,
        4 => next.easy,
        _ => return Err(invalid("invalid simulated grade")),
    })
}

fn performance_probability(r: f32, day: i64, config: &RwkvComparisonConfig) -> f32 {
    let days = i64::from(config.days_to_simulate);
    let start = days / 3;
    let end = (days * 2) / 3;
    let delta = match config.scenario {
        RwkvPerformanceScenario::Steady => 0.0,
        RwkvPerformanceScenario::TemporaryDrop if day >= start && day < end => -0.15,
        RwkvPerformanceScenario::PersistentDrop if day >= start => -0.15,
        RwkvPerformanceScenario::TemporaryImprovement if day >= start && day < end => 0.10,
        _ => 0.0,
    };
    (r + delta).clamp(0.0, 1.0)
}

pub(super) fn paired_grade(
    model: &RwkvWorkloadReviewModel,
    r: f32,
    card_id: i64,
    reps: i64,
    seed: u32,
) -> u8 {
    let quantile = simulation_unit_hash(
        card_id ^ (i64::from(seed).wrapping_mul(0x5851_F42D_4C95_7F2D)),
        0,
        reps,
    );
    let probabilities = model.probabilities_for(r);
    let success_total = probabilities[1] + probabilities[2] + probabilities[3];
    let shares = if success_total > 0.0 {
        [
            probabilities[1] / success_total,
            probabilities[2] / success_total,
            probabilities[3] / success_total,
        ]
    } else {
        [0.2, 0.7, 0.1]
    };
    let again = 1.0 - r;
    let thresholds = [
        again,
        again + r * shares[0],
        again + r * (shares[0] + shares[1]),
    ];
    thresholds
        .iter()
        .position(|threshold| quantile < f64::from(*threshold))
        .map_or(4, |index| index as u8 + 1)
}

/// Distribute scaled fractional daily limits over time instead of rounding
/// every day up, which can badly exaggerate a small sample's workload.
fn day_budget(limit: f32, day: i64) -> usize {
    let limit = f64::from(limit);
    (((day + 1) as f64 * limit).floor() - (day as f64 * limit).floor()).max(0.0) as usize
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn fractional_daily_limit_is_distributed_without_inflation() {
        assert_eq!(
            (0..8).map(|day| day_budget(0.25, day)).collect::<Vec<_>>(),
            vec![0, 0, 0, 1, 0, 0, 0, 1]
        );
        assert_eq!(day_budget(3.0, 4), 3);
    }

    #[test]
    fn comparison_limits_parallelism_for_large_worker_states() {
        assert_eq!(comparison_worker_count(1, 20, 8), 4);
        assert_eq!(comparison_worker_count(128 * 1024 * 1024, 20, 8), 2);
        assert_eq!(comparison_worker_count(512 * 1024 * 1024, 20, 8), 1);
        assert_eq!(comparison_worker_count(1, 2, 8), 2);
        assert_eq!(comparison_worker_count(1, 20, 1), 1);
    }

    #[test]
    fn performance_scenarios_change_only_the_intended_days() {
        let mut config = test_config();
        config.scenario = RwkvPerformanceScenario::TemporaryDrop;
        assert_eq!(performance_probability(0.8, 2, &config), 0.8);
        assert!((performance_probability(0.8, 3, &config) - 0.65).abs() < 1e-6);
        assert_eq!(performance_probability(0.8, 6, &config), 0.8);
        config.scenario = RwkvPerformanceScenario::PersistentDrop;
        assert!((performance_probability(0.8, 9, &config) - 0.65).abs() < 1e-6);
        config.scenario = RwkvPerformanceScenario::TemporaryImprovement;
        assert!((performance_probability(0.8, 4, &config) - 0.9).abs() < 1e-6);
    }

    #[test]
    fn paired_randomness_is_stable_and_responds_to_performance() {
        let model = test_config().review_model;
        assert_eq!(
            paired_grade(&model, 0.9, 123, 4, 2),
            paired_grade(&model, 0.9, 123, 4, 2)
        );
        let low = (0..1000)
            .filter(|id| paired_grade(&model, 0.4, *id, 1, 0) == 1)
            .count();
        let high = (0..1000)
            .filter(|id| paired_grade(&model, 0.9, *id, 1, 0) == 1)
            .count();
        assert!(low > high);
    }

    #[test]
    fn background_replay_excludes_sampled_cards_and_uses_a_repeating_year() {
        let Some((_inference, inputs)) = comparison_fixture() else {
            return;
        };
        let (prepared, _) = prepare_cards(vec![inputs[0].clone()]).unwrap();
        let review = |input: &RwkvComparisonCard| {
            let mut answer = input.input.review_input.clone();
            answer.is_query = false;
            answer.ease = Some(3);
            RwkvComparisonBackgroundReview {
                input: answer,
                pattern_day: 2,
                second_of_day: 123,
                last_review_day: -1,
                last_review_second: -86_000,
            }
        };
        let reviews = [review(&inputs[0]), review(&inputs[1])];
        let background = ComparisonBackground::new(&reviews, &prepared).unwrap();
        assert!(background.for_day(1).is_empty());
        assert_eq!(background.for_day(2).len(), 1);
        assert_eq!(
            background.for_day(367)[0].input.card_id,
            inputs[1].input.review_input.card_id
        );
    }

    #[test]
    fn background_elapsed_time_starts_at_the_latest_real_answer() {
        let Some((inference, inputs)) = comparison_fixture() else {
            return;
        };
        let mut input = inputs[1].input.review_input.clone();
        input.is_query = false;
        input.ease = Some(3);
        let card_id = input.card_id;
        let review = RwkvComparisonBackgroundReview {
            input,
            pattern_day: 0,
            second_of_day: 90,
            last_review_day: -2,
            last_review_second: -2 * 86_400,
        };
        let mut worker = inference.workload_worker(inference.features.clone(), HashMap::new());
        let mut states = SimulationStates::new(&inference.warm_up_states);
        let initial_days = worker.features.card_elapsed_days_cumulative[&card_id];
        let initial_seconds = worker.features.card_elapsed_seconds_cumulative[&card_id];
        let mut replay = BackgroundReplay::default();
        for day in [0, 365] {
            replay
                .replay_until(&[&review], day, 1, &mut 0, &mut worker, &mut states)
                .unwrap();
        }
        assert_eq!(
            worker.features.card_elapsed_days_cumulative[&card_id] - initial_days,
            2 + 365
        );
        assert_eq!(
            worker.features.card_elapsed_seconds_cumulative[&card_id] - initial_seconds,
            367 * 86_400 + 90
        );
    }

    #[test]
    fn batched_background_preserves_sequential_state_and_features() {
        for precision in [StatePrecision::F32, StatePrecision::Int4CardsInt8Notes] {
            check_batched_background_state(precision);
        }
    }

    fn check_batched_background_state(precision: StatePrecision) {
        let Some((inference, inputs)) = comparison_fixture_with_precision(precision) else {
            return;
        };
        let answers = (0..24)
            .map(|index| {
                let mut answer = inputs[index % 2].input.review_input.clone();
                answer.is_query = false;
                answer.ease = Some((index % 4 + 1) as u8);
                answer.duration_millis = Some(8000);
                answer.day_offset = Some(101 + (index / 4) as i64);
                answer.current_elapsed_days = Some((index % 3) as i64);
                answer.current_elapsed_seconds = Some((index % 3 * 86_400) as i64);
                answer
            })
            .collect::<Vec<_>>();
        let mut sequential = inference.workload_worker(inference.features.clone(), HashMap::new());
        let mut batched = inference.workload_worker(inference.features.clone(), HashMap::new());
        let mut sequential_states = SimulationStates::new(&inference.warm_up_states);
        let mut batched_states = SimulationStates::new(&inference.warm_up_states);
        for answer in &answers {
            sequential
                .apply_simulation_answer(answer, &mut sequential_states)
                .unwrap();
        }
        replay_background_batch(&answers, &mut batched, &mut batched_states);
        assert_eq!(sequential.cache_state(), batched.cache_state());
        for input in &inputs {
            let query = &input.input.review_input;
            let expected_view = sequential_states.state_view(query);
            let actual_view = batched_states.state_view(query);
            let expected = expected_view.as_ref();
            let actual = actual_view.as_ref();
            for (expected, actual) in [
                (expected.card, actual.card),
                (expected.note, actual.note),
                (expected.deck, actual.deck),
                (expected.preset, actual.preset),
                (expected.global, actual.global),
            ] {
                assert_eq!(
                    expected.map(serialize_module_state),
                    actual.map(serialize_module_state)
                );
            }
        }
    }

    #[test]
    fn both_models_score_each_trajectory_and_the_same_background_baseline() {
        let Some((inference, inputs)) = comparison_fixture() else {
            return;
        };
        let initial_cache = inference.cache_state();
        let mut answer = inputs[1].input.review_input.clone();
        answer.is_query = false;
        answer.ease = Some(1);
        answer.duration_millis = Some(12_000);
        let background = RwkvComparisonBackgroundReview {
            input: answer,
            pattern_day: 0,
            second_of_day: 30,
            last_review_day: -2,
            last_review_second: -2 * 86_400,
        };
        let config = |with_background: bool| {
            let mut config = test_config();
            config.min_dr = 90;
            config.max_dr = 90;
            config.days_to_simulate = 2;
            config.review_limit = 0.0;
            config.background = if with_background {
                vec![background.clone()]
            } else {
                vec![]
            };
            config
        };
        let target = vec![inputs[0].clone()];
        let without = inference
            .compare_workloads_from_warm_up(target.clone(), config(false), &mut |_, _| Ok(()))
            .unwrap();
        let with = inference
            .compare_workloads_from_warm_up(target, config(true), &mut |_, _| Ok(()))
            .unwrap();
        for (fsrs, rwkv) in [
            (&with.fsrs_scored_by_fsrs, &with.rwkv_scored_by_fsrs),
            (&with.fsrs_scored_by_rwkv, &with.rwkv_scored_by_rwkv),
        ] {
            assert_eq!(fsrs.points[0].1.review_count, 0);
            assert_eq!(fsrs.points[0].1.cost, 0.0);
            assert_eq!(fsrs.points[0].1.memorized, fsrs.reviewless_end_memorized);
            assert_eq!(rwkv.points[0].1.memorized, rwkv.reviewless_end_memorized);
        }
        assert_eq!(
            without.fsrs_scored_by_fsrs.reviewless_end_memorized,
            with.fsrs_scored_by_fsrs.reviewless_end_memorized
        );
        assert_ne!(
            without.fsrs_scored_by_rwkv.reviewless_end_memorized,
            with.fsrs_scored_by_rwkv.reviewless_end_memorized
        );
        assert_eq!(initial_cache, inference.cache_state());
    }

    fn comparison_fixture() -> Option<(RwkvInference, Vec<RwkvComparisonCard>)> {
        comparison_fixture_with_precision(StatePrecision::F32)
    }

    fn comparison_fixture_with_precision(
        precision: StatePrecision,
    ) -> Option<(RwkvInference, Vec<RwkvComparisonCard>)> {
        let path = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join(if SMALL_MODEL {
            "../qt/aqt/rwkv_inference/sm40v2/weights.f32.bin"
        } else {
            "../qt/aqt/rwkv_inference/RWKV_trained_on_5000_10000.bin"
        });
        if !path.exists() {
            return None;
        }
        let mut inference =
            RwkvInference::load_with_precision(path, 0.9, 36500, precision).unwrap();
        let mut inputs = Vec::new();
        for card_id in [101, 102] {
            let answer = ReviewInput {
                card_id,
                note_id: Some(card_id),
                deck_id: Some(1),
                preset_id: Some(1),
                is_query: false,
                ease: Some(3),
                duration_millis: Some(8000),
                card_type: Some(CardType::Review as i64),
                day_offset: Some(100),
                current_elapsed_days: Some(10),
                current_elapsed_seconds: Some(864000),
                target_retentions: [None; 4],
                enforce_grade_order: true,
            };
            inference
                .warm_up_reviews(vec![answer.clone()], false)
                .unwrap();
            let mut query = answer;
            query.is_query = true;
            query.ease = None;
            query.duration_millis = None;
            inputs.push(RwkvComparisonCard {
                input: RwkvWorkloadSimulationInput {
                    review_input: query,
                    interval_days: Some(10),
                    ease_factor: Some(2500),
                    reps: Some(2),
                    lapses: Some(0),
                },
                due_day: 0,
                fsrs_memory: Some((10.0, 5.0, 10.0)),
                fsrs_parameters: vec![],
                historical_retention: 0.9,
            });
        }
        Some((inference, inputs))
    }

    #[test]
    fn shared_rwkv_state_matches_independent_scheduler_and_learner() {
        let Some((inference, inputs)) = comparison_fixture() else {
            return;
        };
        let (prepared, _) = prepare_cards(inputs).unwrap();
        let cards = initial_cards(&prepared);
        let mut queries = cards
            .iter()
            .map(|card| simulation_query_input_for_card(&card.card, 0.9, 0))
            .collect::<Vec<_>>();
        let mut new_card = queries[0].clone();
        new_card.card_id = 999;
        new_card.note_id = Some(999);
        new_card.card_type = Some(CardType::New as i64);
        queries.push(new_card);
        // A shared RWKV state must behave exactly like independent scheduler
        // and learner states, even when cards share note and deck streams.
        let mut shared_worker =
            inference.workload_worker(inference.features.clone(), HashMap::new());
        let mut shared_states = SimulationStates::new(&inference.warm_up_states);
        let mut scheduler_worker =
            inference.workload_worker(inference.features.clone(), HashMap::new());
        let mut scheduler_states = SimulationStates::new(&inference.warm_up_states);
        let mut learner_worker =
            inference.workload_worker(inference.features.clone(), HashMap::new());
        let mut learner_states = SimulationStates::new(&inference.warm_up_states);
        for (index, day, ease) in [(0, 0, 4), (1, 0, 1), (0, 4, 2), (1, 5, 3), (2, 7, 3)] {
            let mut query = queries[index].clone();
            if index < 2 {
                query.note_id = Some(101);
            }
            query.day_offset = Some(100 + day);
            query.current_elapsed_days = Some(day + 2);
            query.current_elapsed_seconds = None;
            let shared_r = shared_worker
                .review_heads_for_state(&query, shared_states.state_view(&query).as_ref())
                .unwrap()
                .retrievability;
            let learner_r = learner_worker
                .review_heads_for_state(&query, learner_states.state_view(&query).as_ref())
                .unwrap()
                .retrievability;
            assert_eq!(shared_r.to_bits(), learner_r.to_bits());
            let shared_interval = shared_worker.selected_answer_intervals_for_inputs(
                std::slice::from_ref(&query),
                &shared_states,
                &[ease],
            );
            let scheduler_interval = scheduler_worker.selected_answer_intervals_for_inputs(
                std::slice::from_ref(&query),
                &scheduler_states,
                &[ease],
            );
            assert_eq!(shared_interval, scheduler_interval);
            let mut answer = simulated_answer_input(&query, ease);
            answer.duration_millis = Some(5000 + i64::from(ease) * 1000);
            shared_worker
                .apply_simulation_answer(&answer, &mut shared_states)
                .unwrap();
            scheduler_worker
                .apply_simulation_answer(&answer, &mut scheduler_states)
                .unwrap();
            learner_worker
                .apply_simulation_answer(&answer, &mut learner_states)
                .unwrap();
            assert_eq!(shared_worker.cache_state(), scheduler_worker.cache_state());
            assert_eq!(shared_worker.cache_state(), learner_worker.cache_state());
            let shared_view = shared_states.state_view(&query);
            let reference_view = learner_states.state_view(&query);
            let shared = shared_view.as_ref();
            let reference = reference_view.as_ref();
            for (actual, expected) in [
                (shared.card, reference.card),
                (shared.note, reference.note),
                (shared.deck, reference.deck),
                (shared.preset, reference.preset),
                (shared.global, reference.global),
            ] {
                assert_eq!(
                    actual.map(serialize_module_state),
                    expected.map(serialize_module_state)
                );
            }
        }
    }

    #[test]
    fn parallel_comparisons_match_sequential_runs_when_performance_changes() {
        let Some((inference, inputs)) = comparison_fixture() else {
            return;
        };
        let (prepared, _) = prepare_cards(inputs).unwrap();
        let background = ComparisonBackground::new(&[], &prepared).unwrap();
        for learner in [RwkvComparisonLearner::Fsrs, RwkvComparisonLearner::Rwkv] {
            for scenario in [
                RwkvPerformanceScenario::TemporaryDrop,
                RwkvPerformanceScenario::PersistentDrop,
            ] {
                let mut config = test_config();
                config.learner = learner;
                config.scenario = scenario;
                config.repetitions = 2;
                let targets = [80, 90];
                let total = 1 + targets.len() as u32 * config.repetitions * 2;
                let mut progress = Vec::new();
                let parallel = inference
                    .compare_targets(
                        &prepared,
                        &config,
                        &background,
                        &targets,
                        total,
                        &mut |current, _| {
                            progress.push(current);
                            Ok(())
                        },
                    )
                    .unwrap();
                assert_eq!(progress.last(), Some(&total));
                assert!(progress.windows(2).all(|pair| pair[0] <= pair[1]));
                let mut parallel = parallel.into_iter();
                for dr in targets {
                    for seed in 0..config.repetitions {
                        for scheduler in [Scheduler::Fsrs, Scheduler::Rwkv] {
                            let sequential = inference
                                .compare_target(
                                    &prepared,
                                    &config,
                                    &background,
                                    ComparisonJob {
                                        dr,
                                        seed,
                                        scheduler,
                                    },
                                    &mut || Ok(()),
                                )
                                .unwrap();
                            let actual = parallel.next().unwrap();
                            for (actual, sequential) in actual.scores.iter().zip(sequential.scores)
                            {
                                assert_eq!(actual.0.to_bits(), sequential.0.to_bits());
                                assert_eq!(actual.1.to_bits(), sequential.1.to_bits());
                            }
                            assert_eq!(actual.cost.to_bits(), sequential.cost.to_bits());
                            assert_eq!(actual.review_count, sequential.review_count);
                        }
                    }
                }
            }
        }
    }

    #[test]
    fn both_schedulers_use_the_same_learner_and_keep_resident_state() {
        let Some((inference, inputs)) = comparison_fixture() else {
            return;
        };
        let cache = inference.cache_state();
        let mut missing_memory = inputs.clone();
        missing_memory[0].fsrs_memory = None;
        let (estimated, estimated_count) = prepare_cards(missing_memory).unwrap();
        assert_eq!(estimated_count, 1);
        assert!(valid_memory(&estimated[0].memory.unwrap()));
        let (prepared, _) = prepare_cards(inputs.clone()).unwrap();
        let cards = initial_cards(&prepared);
        let states = SimulationStates::new(&inference.warm_up_states);
        let mut worker = inference.workload_worker(inference.features.clone(), HashMap::new());
        let queries = cards
            .iter()
            .map(|card| simulation_query_input_for_card(&card.card, 0.9, 0))
            .collect::<Vec<_>>();
        let reference = worker.workload_query_predictions_for_inputs(&queries, &states);
        let optimized = scheduler_predictions(
            &cards,
            &[0, 1],
            &mut worker,
            &states,
            0.9,
            0,
            Scheduler::Rwkv,
        );
        for (query, (expected, actual)) in queries.iter().zip(reference.iter().zip(&optimized)) {
            assert!((expected.retrievability - actual.retrievability).abs() < 1e-6);
            let scalar = worker
                .review_heads_for_state(query, states.state_view(query).as_ref())
                .unwrap();
            assert!((expected.retrievability - scalar.retrievability).abs() < 1e-6);
        }
        for learner in [RwkvComparisonLearner::Fsrs, RwkvComparisonLearner::Rwkv] {
            let mut steady = test_config();
            steady.min_dr = 90;
            steady.max_dr = 90;
            steady.review_limit = 0.0;
            steady.learner = learner;
            let normal = inference
                .compare_workloads_from_warm_up(inputs.clone(), steady, &mut |_, _| Ok(()))
                .unwrap();
            let mut drop = test_config();
            drop.min_dr = 90;
            drop.max_dr = 90;
            drop.review_limit = 0.0;
            drop.learner = learner;
            drop.scenario = RwkvPerformanceScenario::PersistentDrop;
            let changed = inference
                .compare_workloads_from_warm_up(inputs.clone(), drop, &mut |_, _| Ok(()))
                .unwrap();
            assert_eq!(normal.fsrs_scored_by_fsrs.points[0].1.review_count, 0);
            assert_eq!(normal.rwkv_scored_by_fsrs.points[0].1.review_count, 0);
            assert!(
                (normal.fsrs_scored_by_fsrs.points[0].1.memorized
                    - normal.rwkv_scored_by_fsrs.points[0].1.memorized)
                    .abs()
                    < 1e-6
            );
            assert!(
                (normal.fsrs_scored_by_fsrs.points[0].1.memorized
                    - normal.fsrs_scored_by_fsrs.reviewless_end_memorized)
                    .abs()
                    < 1e-6
            );
            assert!(
                (normal.fsrs_scored_by_fsrs.points[0].1.memorized
                    - changed.fsrs_scored_by_fsrs.points[0].1.memorized
                    - 0.3)
                    .abs()
                    < 1e-5
            );
            assert_eq!(
                changed.fsrs_scored_by_fsrs.points[0].1.memorized,
                changed.rwkv_scored_by_fsrs.points[0].1.memorized
            );

            let mut future_inputs = inputs.clone();
            for item in &mut future_inputs {
                item.due_day = 10;
            }
            let mut future = test_config();
            future.min_dr = 90;
            future.max_dr = 90;
            future.learner = learner;
            let future = inference
                .compare_workloads_from_warm_up(future_inputs, future, &mut |_, _| Ok(()))
                .unwrap();
            assert_eq!(future.fsrs_scored_by_fsrs.points[0].1.review_count, 0);
            assert_eq!(future.rwkv_scored_by_fsrs.points[0].1.review_count, 0);

            let mut new_card = inputs[0].clone();
            new_card.input.review_input.card_type = Some(CardType::New as i64);
            new_card.fsrs_memory = None;
            let mut new_config = test_config();
            new_config.min_dr = 90;
            new_config.max_dr = 90;
            new_config.days_to_simulate = 1;
            new_config.new_limit = 1.0;
            new_config.review_limit = 1.0;
            new_config.learner = learner;
            let introduced = inference
                .compare_workloads_from_warm_up(vec![new_card], new_config, &mut |_, _| Ok(()))
                .unwrap();
            assert_eq!(introduced.fsrs_scored_by_fsrs.points[0].1.review_count, 1);
            assert_eq!(introduced.rwkv_scored_by_fsrs.points[0].1.review_count, 1);
            assert_eq!(
                introduced.fsrs_scored_by_fsrs.points[0].1.cost,
                introduced.rwkv_scored_by_fsrs.points[0].1.cost
            );
            assert_eq!(
                introduced.fsrs_scored_by_fsrs.points[0].1.memorized,
                introduced.rwkv_scored_by_fsrs.points[0].1.memorized
            );

            let active_config = || {
                let mut config = test_config();
                config.min_dr = 90;
                config.max_dr = 90;
                config.learner = learner;
                config.repetitions = 2;
                config
            };
            let first = inference
                .compare_workloads_from_warm_up(inputs.clone(), active_config(), &mut |_, _| Ok(()))
                .unwrap();
            let second = inference
                .compare_workloads_from_warm_up(inputs.clone(), active_config(), &mut |_, _| Ok(()))
                .unwrap();
            assert!(first.fsrs_scored_by_fsrs.points[0].1.review_count > 0);
            assert!(first.rwkv_scored_by_fsrs.points[0].1.review_count > 0);
            assert_eq!(
                first.fsrs_scored_by_fsrs.points[0].1.review_count,
                second.fsrs_scored_by_fsrs.points[0].1.review_count
            );
            assert_eq!(
                first.rwkv_scored_by_fsrs.points[0].1.memorized.to_bits(),
                second.rwkv_scored_by_fsrs.points[0].1.memorized.to_bits()
            );
            assert_eq!(
                first.fsrs_scored_by_fsrs.reviewless_end_memorized,
                first.rwkv_scored_by_fsrs.reviewless_end_memorized
            );
        }
        let mut config = test_config();
        config.min_dr = 90;
        config.max_dr = 90;
        let mut callbacks = 0;
        let result = inference.compare_workloads_from_warm_up(inputs, config, &mut |_, _| {
            callbacks += 1;
            if callbacks == 4 {
                Err(io::Error::new(io::ErrorKind::Interrupted, "cancelled"))
            } else {
                Ok(())
            }
        });
        assert_eq!(result.err().unwrap().kind(), io::ErrorKind::Interrupted);
        assert_eq!(cache, inference.cache_state());
    }

    fn test_config() -> RwkvComparisonConfig {
        RwkvComparisonConfig {
            min_dr: 80,
            max_dr: 90,
            target_dr_step: 5,
            days_to_simulate: 9,
            review_limit: 10.0,
            new_limit: 0.0,
            new_cards_ignore_review_limit: false,
            max_interval: 36500,
            review_order: ReviewCardOrder::Day,
            suspend_after_lapses: None,
            learner: RwkvComparisonLearner::Fsrs,
            scenario: RwkvPerformanceScenario::Steady,
            repetitions: 1,
            output_scale: 1.0,
            background: vec![],
            review_model: RwkvWorkloadReviewModel {
                grade_seconds: [12.0, 9.0, 7.0, 5.0],
                bucket_probabilities: vec![],
            },
        }
    }
}
