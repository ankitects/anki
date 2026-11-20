// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Instant forecast driven by recorded review timing patterns.

use anki_proto::deck_config::deck_config::config::LeechAction;
use anki_proto::deck_config::deck_config::config::NewCardGatherPriority;
use anki_proto::deck_config::deck_config::config::ReviewMix;
use fsrs::MemoryState;
use fsrs::FSRS;

use super::instant_forecast_queue::ignores_review_limit;
use super::instant_forecast_queue::instant_due;
use super::instant_forecast_queue::instant_study_candidates;
use super::instant_forecast_queue::InstantCandidate;
use super::instant_forecast_queue::InstantReviewPolicy;
use super::instant_forecast_sampling::day_deviations;
use super::instant_forecast_sampling::AnswerStratum;
use super::instant_forecast_sampling::PREVIEW_LIMIT;
use super::workload_comparison::paired_grade;
use super::workload_forecast::summarize_forecast;
use super::workload_forecast::ForecastPath;
use super::*;
use crate::deckconfig::DeckConfig;
use crate::decks::Deck;
use crate::decks::DeckId;
use crate::scheduler::answering::PreviewDelays;
use crate::scheduler::fsrs::memory_state::fsrs_memory_state_for_params;
use crate::scheduler::fsrs::simulator::normalized_fsrs_parameters;
use crate::scheduler::queue::Intersperser;
use crate::scheduler::rwkv::rwkv_repeat_spacing_eligibility;
use crate::scheduler::rwkv::RwkvReviewScoreEligibility;
use crate::scheduler::states::fuzz::ReviewFuzzConfig;
use crate::scheduler::states::interval_kind::IntervalKind;
use crate::scheduler::states::steps::LearningSteps;
use crate::scheduler::states::CardState;
use crate::scheduler::states::LearnState;
use crate::scheduler::states::NewState;
use crate::scheduler::states::NormalState;
use crate::scheduler::states::RelearnState;
use crate::scheduler::states::ReviewState as SchedulingReviewState;
use crate::scheduler::states::StateContext;

const DAY_MILLIS: i64 = 86_400_000;
const SESSION_BREAK_MILLIS: u32 = 30 * 60 * 1000;
const DECODED_BASE_BUDGET_BYTES: usize = 256 * 1024 * 1024;
const DECODED_PATH_BUDGET_BYTES: usize = 16 * 1024 * 1024;

struct InstantPathParameters {
    seed: u32,
    recall_offset: f32,
    stratum: Option<usize>,
}

/// Wall time spent in each phase of one path's answer loop, logged once at
/// the end of the path to locate forecast cost.
#[derive(Default)]
struct InstantPhaseTimes {
    refresh: std::time::Duration,
    refreshes: u64,
    refreshed_cards: u64,
    candidates: std::time::Duration,
    lists: std::time::Duration,
    select: std::time::Duration,
    preview: std::time::Duration,
    answer_score: std::time::Duration,
    apply: std::time::Duration,
    post_score: std::time::Duration,
    iterations: u64,
}

impl InstantPhaseTimes {
    fn log(&self, seed: u32, stratum: Option<usize>, answers: u32, total: std::time::Duration) {
        let ms = |duration: std::time::Duration| duration.as_secs_f64() * 1000.0;
        tracing::debug!(
            seed,
            stratum,
            answers,
            iterations = self.iterations,
            refreshes = self.refreshes,
            refreshed_cards = self.refreshed_cards,
            total_ms = ms(total),
            refresh_ms = ms(self.refresh),
            candidates_ms = ms(self.candidates),
            lists_ms = ms(self.lists),
            select_ms = ms(self.select),
            preview_ms = ms(self.preview),
            answer_score_ms = ms(self.answer_score),
            apply_ms = ms(self.apply),
            post_score_ms = ms(self.post_score),
            "RWKV Instant forecast path phases"
        );
    }
}

struct InstantPathProgress<'a> {
    check_cancel: &'a mut dyn FnMut() -> io::Result<()>,
    report_day: &'a mut dyn FnMut(u32, [f32; 5]) -> io::Result<()>,
}

struct InstantCard {
    input: RwkvInstantForecastCard,
    last_day: Option<i64>,
    last_millis: Option<i64>,
    last_answer_sequence: Option<u32>,
    score: f32,
    score_in_map: bool,
    reps: i64,
    state: CardState,
    learning_due_millis: i64,
    fsrs: Option<Arc<FSRS>>,
    last_kind_and_grade: Option<(i64, i64)>,
}

impl InstantCard {
    fn new(
        input: RwkvInstantForecastCard,
        now_millis: u32,
        models: &mut HashMap<Vec<u32>, Arc<FSRS>>,
    ) -> io::Result<(Self, bool)> {
        let review = &input.input.review_input;
        let is_new = review.card_type == Some(CardType::New as i64);
        let mut estimated = false;
        let (fsrs, memory) = if input.fsrs_enabled {
            let params = normalized_fsrs_parameters(&input.fsrs_parameters)
                .map_err(|error| io::Error::new(io::ErrorKind::InvalidInput, error.to_string()))?;
            let key = params
                .iter()
                .map(|value| value.to_bits())
                .collect::<Vec<_>>();
            let model = match models.get(&key) {
                Some(model) => Arc::clone(model),
                None => {
                    let model = Arc::new(FSRS::new(&params).map_err(|error| {
                        io::Error::new(io::ErrorKind::InvalidInput, error.to_string())
                    })?);
                    models.insert(key, Arc::clone(&model));
                    model
                }
            };
            let memory = input
                .fsrs_memory
                .map(|(stability, difficulty, stability_fast)| MemoryState {
                    stability,
                    difficulty,
                    stability_fast,
                })
                .filter(super::workload_comparison::valid_memory);
            let memory = if memory.is_some() || is_new {
                memory
            } else {
                estimated = true;
                Some(
                    model
                        .memory_state_from_sm2(
                            input.input.ease_factor.unwrap_or(2500).max(1300) as f32 / 1000.0,
                            input.input.interval_days.unwrap_or(1).max(1) as f32,
                            input.fsrs_historical_retention,
                        )
                        .map_err(|error| {
                            io::Error::new(io::ErrorKind::InvalidInput, error.to_string())
                        })?,
                )
            };
            (Some(model), memory)
        } else {
            (None, None)
        };
        let review_state = SchedulingReviewState {
            scheduled_days: input.input.interval_days.unwrap_or(1).max(1) as u32,
            elapsed_days: review.current_elapsed_days.unwrap_or(0).max(0) as u32,
            ease_factor: input.input.ease_factor.unwrap_or(2500).max(1300) as f32 / 1000.0,
            lapses: input.input.lapses.unwrap_or(0).max(0) as u32,
            memory_state: memory.map(Into::into),
            ..Default::default()
        };
        let learning = LearnState {
            remaining_steps: input.remaining_steps,
            scheduled_secs: 0,
            elapsed_secs: review.current_elapsed_seconds.unwrap_or(0).max(0) as u32,
            memory_state: memory.map(Into::into),
        };
        let state = match review.card_type {
            Some(0) => NewState {
                position: input.new_position,
            }
            .into(),
            Some(1) => learning.into(),
            Some(2) => review_state.into(),
            Some(3) => RelearnState {
                learning,
                review: review_state,
            }
            .into(),
            _ => {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidInput,
                    "Instant forecast cannot simulate this card state",
                ))
            }
        };
        let last_millis = review
            .current_elapsed_seconds
            .map(|seconds| i64::from(now_millis) - seconds * 1000);
        let last_day = if is_new {
            None
        } else {
            review
                .current_elapsed_days
                .map(|days| -days)
                .or_else(|| last_millis.map(|last| last.div_euclid(DAY_MILLIS)))
        };
        Ok((
            Self {
                last_day,
                last_millis,
                reps: input.input.reps.unwrap_or(0),
                learning_due_millis: input.learning_due_millis,
                last_kind_and_grade: input.last_kind_and_grade,
                input,
                last_answer_sequence: None,
                score: f32::NAN,
                score_in_map: false,
                state,
                fsrs,
            },
            estimated,
        ))
    }

    fn query(&self, day: i64, millis: i64) -> ReviewInput {
        let mut query = self.input.input.review_input.clone();
        query.is_query = true;
        query.ease = None;
        query.duration_millis = None;
        query.day_offset = query.day_offset.map(|today| today + day);
        query.current_elapsed_seconds = self.last_millis.map(|last| (millis - last).max(0) / 1000);
        query.current_elapsed_days = if self.input.queue == 0 {
            query
                .current_elapsed_seconds
                .map(|seconds| seconds / 86_400)
        } else if matches!(self.state, CardState::Normal(NormalState::Learning(_))) {
            None
        } else {
            self.last_day.map(|last| (day - last).max(0))
        };
        query
    }

    fn candidate(&self, day: i64, millis: i64, answers: u32) -> InstantCandidate {
        let reviewed_today = self.last_day == Some(day);
        InstantCandidate {
            card_id: self.input.input.review_input.card_id,
            deck_id: self.input.group,
            score: self.score,
            target: self.input.desired_retention,
            instant_enabled: self.input.instant_enabled,
            due_day: self.input.due_day,
            interval_days: self.input.input.interval_days.unwrap_or(1),
            ease_factor: self.input.input.ease_factor.unwrap_or(0),
            reviewed_today,
            elapsed_seconds: self
                .last_millis
                .map(|last| ((millis - last).max(0) / 1000).min(i64::from(u32::MAX)) as u32),
            intervening_reviews: self.last_answer_sequence.map_or_else(
                || {
                    self.input
                        .intervening_reviews
                        .map(|count| count.saturating_add(answers))
                },
                |sequence| Some(answers.saturating_sub(sequence)),
            ),
        }
    }

    #[allow(clippy::too_many_arguments)]
    fn answer(
        &mut self,
        day: i64,
        millis: i64,
        grade: i64,
        preset: &DeckConfig,
        fsrs_learning_queues_disabled: bool,
        fsrs_short_term_with_steps_enabled: bool,
        seed: u32,
    ) -> io::Result<()> {
        let elapsed_days = self.last_day.map_or(0, |last| (day - last).max(0) as u32);
        let elapsed_secs = self.last_millis.map_or(0, |last| {
            ((millis - last).max(0) / 1000).min(u32::MAX as i64) as u32
        });
        let (current, memory, intraday_learning) = match self.state {
            CardState::Normal(NormalState::New(state)) => (state.into(), None, false),
            CardState::Normal(NormalState::Learning(mut state)) => {
                state.elapsed_secs = elapsed_secs;
                (state.into(), state.memory_state, self.input.queue == 1)
            }
            CardState::Normal(NormalState::Review(mut state)) => {
                state.elapsed_days = elapsed_days;
                (state.into(), state.memory_state, false)
            }
            CardState::Normal(NormalState::Relearning(mut state)) => {
                state.learning.elapsed_secs = elapsed_secs;
                state.review.elapsed_days = elapsed_days;
                (
                    state.into(),
                    state.learning.memory_state,
                    self.input.queue == 1,
                )
            }
            _ => {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidInput,
                    "filtered cards are not supported in Instant forecasts",
                ))
            }
        };
        let params = &self.input.fsrs_parameters;
        let fractional = crate::scheduler::fsrs::uses_fractional_intervals(params);
        let elapsed = if fractional || intraday_learning {
            elapsed_secs as f32 / 86_400.0
        } else {
            elapsed_days as f32
        };
        let fsrs_next_states = self
            .fsrs
            .as_ref()
            .map(|model| {
                model
                    .next_states_with_elapsed_days(
                        memory.map(Into::into),
                        self.input.desired_retention,
                        elapsed,
                    )
                    .map_err(|error| io::Error::new(io::ErrorKind::InvalidInput, error.to_string()))
            })
            .transpose()?;
        let inner = &preset.inner;
        let fsrs_again_s90 = if inner.leech_only_if_young {
            fsrs_next_states
                .as_ref()
                .map(|states| {
                    fsrs_memory_state_for_params(params, states.again.memory)
                        .map(|memory| memory.stability)
                        .map_err(|error| {
                            io::Error::new(io::ErrorKind::InvalidInput, error.to_string())
                        })
                })
                .transpose()?
        } else {
            None
        };
        let context = StateContext {
            fuzz_factor: Some(simulation_unit_hash(
                self.input.input.review_input.card_id,
                self.reps,
                i64::from(seed),
            ) as f32),
            fsrs_next_states,
            fsrs_short_term_with_steps_enabled,
            fsrs_learning_queues_disabled,
            fsrs_allow_short_term: params.is_empty()
                || (params.len() >= 19 && params[17] > 0.0 && params[18] > 0.0),
            fsrs_fractional_intervals: fractional,
            steps: LearningSteps::new(&inner.learn_steps),
            graduating_interval_good: inner.graduating_interval_good,
            graduating_interval_easy: inner.graduating_interval_easy,
            initial_ease_factor: inner.initial_ease,
            hard_multiplier: inner.hard_multiplier,
            easy_multiplier: inner.easy_multiplier,
            interval_multiplier: inner.interval_multiplier,
            review_fuzz_config: ReviewFuzzConfig::default(),
            maximum_review_interval: inner.maximum_review_interval,
            fsrs_minimum_interval_secs: inner.fsrs_minimum_interval_secs,
            leech_threshold: inner.leech_threshold,
            count_review_lapse: self.last_day != Some(day),
            leech_only_if_young: inner.leech_only_if_young,
            fsrs_again_s90,
            load_balancer_ctx: None,
            relearn_steps: LearningSteps::new(&inner.relearn_steps),
            lapse_multiplier: inner.lapse_multiplier,
            minimum_lapse_interval: inner.minimum_lapse_interval,
            in_filtered_deck: false,
            preview_delays: PreviewDelays::default(),
        };
        let kind = match current {
            CardState::Normal(NormalState::New(_))
            | CardState::Normal(NormalState::Learning(_)) => 0,
            CardState::Normal(NormalState::Review(_)) => 1,
            CardState::Normal(NormalState::Relearning(_)) => 2,
            _ => unreachable!(),
        };
        let states = current.next_states(&context);
        let next = match grade {
            1 => states.again,
            2 => states.hard,
            3 => states.good,
            4 => states.easy,
            _ => {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidInput,
                    "invalid forecast answer",
                ))
            }
        };
        let secs_to_rollover = ((DAY_MILLIS - millis.rem_euclid(DAY_MILLIS)) / 1000) as u32;
        let interval = next.interval_kind().maybe_as_days(secs_to_rollover);
        self.input.queue = match next {
            CardState::Normal(NormalState::Review(_)) => 2,
            CardState::Normal(NormalState::Learning(_) | NormalState::Relearning(_)) => {
                match interval {
                    IntervalKind::InSecs(_) => 1,
                    IntervalKind::InDays(_) => 3,
                }
            }
            _ => {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidInput,
                    "unsupported Instant forecast transition",
                ))
            }
        };
        if next.leeched() && inner.leech_action() == LeechAction::Suspend {
            self.input.queue = -1;
        }
        match interval {
            IntervalKind::InSecs(seconds) => {
                self.learning_due_millis = millis + i64::from(seconds) * 1000
            }
            IntervalKind::InDays(days) => {
                self.learning_due_millis = (day + i64::from(days)) * DAY_MILLIS;
                self.input.due_day = day + i64::from(days);
            }
        }
        self.state = next;
        self.input.input.review_input.card_type = Some(match next {
            CardState::Normal(NormalState::Learning(_)) => 1,
            CardState::Normal(NormalState::Review(_)) => 2,
            CardState::Normal(NormalState::Relearning(_)) => 3,
            _ => unreachable!(),
        });
        if let Some(review) = next.review_state() {
            self.input.input.interval_days = Some(i64::from(review.scheduled_days));
            self.input.input.ease_factor = Some((review.ease_factor * 1000.0).round() as i64);
            self.input.input.lapses = Some(i64::from(review.lapses));
        }
        self.last_kind_and_grade = Some((kind, grade));
        Ok(())
    }
}

impl RwkvInference {
    pub fn forecast_instant_workload_from_warm_up(
        &self,
        inputs: Vec<RwkvInstantForecastCard>,
        config: RwkvInstantForecastConfig,
        progress: &mut dyn FnMut(u32, u32) -> io::Result<()>,
        day_progress: &mut dyn FnMut(u32, u32, [f32; 5]) -> io::Result<()>,
    ) -> io::Result<RwkvWorkloadForecastOutput> {
        if !(1..=365).contains(&config.days) || !(1..=2000).contains(&config.paths) {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "invalid forecast size",
            ));
        }
        if config.scenario_sigma.is_some_and(|sigma| {
            config.paths != 3 || !sigma.is_finite() || !(0.0..=1.0).contains(&sigma)
        }) {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "scenario sigma requires three paths and must be between 0 and 1",
            ));
        }
        if config.stratified_answers
            && (config.paths != 3
                || config.scenario_sigma.is_some()
                || !config.stratified_scale.is_finite()
                || config.stratified_scale < 0.0
                || !config.stratified_bias_sd.is_finite()
                || config.stratified_bias_sd < 0.0
                || !config.stratified_review_variance.is_finite()
                || config.stratified_review_variance < 0.0)
        {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "stratified answers require three paths, no scenario sigma, and nonnegative finite scale, bias and variance",
            ));
        }
        if config
            .review_time_model
            .as_ref()
            .is_some_and(|model| !model.valid())
        {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "invalid Instant review-time model",
            ));
        }
        if config.decks.is_empty()
            || config.now_millis as i64 >= DAY_MILLIS
            || config.timing_patterns.is_empty()
            || config.timing_pattern_success_shares.len() != config.timing_patterns.len()
            || config.timing_pattern_success_shares.iter().any(|shares| {
                shares
                    .iter()
                    .any(|value| !value.is_finite() || *value < 0.0)
                    || shares[1..].iter().sum::<f32>() <= 0.0
            })
            || config.timing_patterns.iter().any(|pattern| {
                pattern.len() < 2
                    || pattern.iter().any(|(at, _)| i64::from(*at) >= DAY_MILLIS)
                    || pattern.windows(2).any(|pair| pair[0].0 >= pair[1].0)
            })
        {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "invalid review timing pattern",
            ));
        }
        let mut group_ids = inputs.iter().map(|card| card.group).collect::<Vec<_>>();
        group_ids.sort_unstable();
        group_ids.dedup();
        progress(0, config.paths)?;
        let ids = StreamIds::for_workload_inputs(
            &inputs
                .iter()
                .map(|card| card.input.clone())
                .collect::<Vec<_>>(),
        );
        let state_len = self.warm_up_states.state_len_for(&ids);
        let decoded =
            ForecastDecodedStates::new(&self.warm_up_states, &ids, DECODED_BASE_BUDGET_BYTES)?;
        let decoded_path_budget = if decoded.bytes == 0 {
            0
        } else {
            DECODED_PATH_BUDGET_BYTES
        };
        let concurrent = (WORKLOAD_TARGET_STATE_BUDGET_BYTES.saturating_sub(decoded.bytes)
            / state_len.saturating_add(decoded_path_budget).max(1))
        .clamp(1, rayon::current_num_threads())
        .min(config.paths as usize);
        enum Message {
            Heartbeat,
            Day(u32, u32, [f32; 5]),
            Finished(u32, io::Result<(ForecastPath, u32)>),
        }
        let next_path = AtomicUsize::new(0);
        let stopped = AtomicBool::new(false);
        let (sender, receiver) = std::sync::mpsc::channel();
        let mut results = (0..config.paths).map(|_| None).collect::<Vec<_>>();
        let mut estimated = 0;
        std::thread::scope(|scope| {
            let (next_path, stopped, inputs, group_ids, config, decoded) =
                (&next_path, &stopped, &inputs, &group_ids, &config, &decoded);
            scope.spawn(move || {
                rayon::scope(|workers| {
                    for _ in 0..concurrent {
                        let sender = sender.clone();
                        workers.spawn(move |_| {
                            while !stopped.load(Ordering::Relaxed) {
                                let slot = next_path.fetch_add(1, Ordering::Relaxed) as u32;
                                if slot >= config.paths {
                                    break;
                                }
                                // Show the central scenario first when memory
                                // limits the forecast to sequential paths.
                                let path = if config.scenario_sigma.is_some()
                                    || config.stratified_answers
                                {
                                    [1, 0, 2][slot as usize]
                                } else {
                                    slot
                                };
                                let mut worker =
                                    self.workload_worker(self.features.clone(), HashMap::new());
                                let mut check_cancel = || {
                                    if stopped.load(Ordering::Relaxed) {
                                        return Err(io::Error::new(
                                            io::ErrorKind::Interrupted,
                                            "Instant forecast cancelled",
                                        ));
                                    }
                                    sender.send(Message::Heartbeat).map_err(|_| {
                                        io::Error::new(
                                            io::ErrorKind::Interrupted,
                                            "Instant forecast cancelled",
                                        )
                                    })
                                };
                                let states = SimulationStates::with_forecast_cache(
                                    &self.warm_up_states,
                                    decoded,
                                    decoded_path_budget,
                                );
                                let mut report_day = |day, values| {
                                    if config.report_days {
                                        sender.send(Message::Day(path, day, values)).map_err(
                                            |_| {
                                                io::Error::new(
                                                    io::ErrorKind::Interrupted,
                                                    "Instant forecast cancelled",
                                                )
                                            },
                                        )?;
                                    }
                                    Ok(())
                                };
                                let result = worker.instant_forecast_path(
                                    inputs,
                                    states,
                                    group_ids,
                                    config,
                                    InstantPathParameters {
                                        seed: if config.scenario_sigma.is_some()
                                            || config.stratified_answers
                                        {
                                            config.seed
                                        } else {
                                            config.seed.wrapping_add(path)
                                        },
                                        recall_offset: config
                                            .scenario_sigma
                                            .map_or(0.0, |sigma| (path as f32 - 1.0) * sigma),
                                        stratum: config.stratified_answers.then_some(path as usize),
                                    },
                                    InstantPathProgress {
                                        check_cancel: &mut check_cancel,
                                        report_day: &mut report_day,
                                    },
                                );
                                if sender.send(Message::Finished(path, result)).is_err() {
                                    break;
                                }
                            }
                        });
                    }
                });
            });
            let mut completed = 0;
            for message in receiver {
                let step = match message {
                    Message::Heartbeat => progress(completed, config.paths),
                    Message::Day(path, day, values) => day_progress(path, day, values),
                    Message::Finished(path, result) => result.and_then(|(record, count)| {
                        results[path as usize] = Some(record);
                        estimated = count;
                        completed += 1;
                        progress(completed, config.paths)
                    }),
                };
                if let Err(error) = step {
                    stopped.store(true, Ordering::Relaxed);
                    return Err(error);
                }
            }
            Ok(())
        })?;
        let paths = results
            .into_iter()
            .map(|path| path.expect("every forecast path completed"))
            .collect::<Vec<_>>();
        let mut output = summarize_forecast(&group_ids, &paths, config.days, estimated);
        if config.scenario_sigma.is_some() || config.stratified_answers {
            output.scenarios = Some(
                paths
                    .iter()
                    .map(|path| {
                        (0..config.days as usize)
                            .map(|day| {
                                [
                                    path.due[day] as f32,
                                    path.done[day] as f32,
                                    path.new[day] as f32,
                                    path.lapses[day] as f32,
                                    path.seconds[day],
                                ]
                            })
                            .collect()
                    })
                    .collect(),
            );
        }
        Ok(output)
    }

    fn instant_forecast_path(
        &mut self,
        inputs: &[RwkvInstantForecastCard],
        mut states: SimulationStates<'_>,
        group_ids: &[i64],
        config: &RwkvInstantForecastConfig,
        parameters: InstantPathParameters,
        progress: InstantPathProgress<'_>,
    ) -> io::Result<(ForecastPath, u32)> {
        let InstantPathParameters {
            seed,
            recall_offset,
            stratum,
        } = parameters;
        let InstantPathProgress {
            check_cancel,
            report_day,
        } = progress;
        let mut decks = config.decks.clone();
        let deck_configs = config
            .deck_configs
            .iter()
            .cloned()
            .map(|config| (config.id, config))
            .collect::<HashMap<_, _>>();
        let deck_order = decks
            .iter()
            .enumerate()
            .map(|(index, deck)| (deck.id.0, index))
            .collect::<HashMap<_, _>>();
        let study_preset = decks
            .iter()
            .find(|deck| deck.id.0 == config.study_deck_id)
            .and_then(|deck| deck.config_id())
            .and_then(|id| deck_configs.get(&id))
            .ok_or_else(|| {
                io::Error::new(
                    io::ErrorKind::InvalidInput,
                    "forecast study deck has no preset",
                )
            })?;
        let score_new = matches!(
            study_preset.inner.new_card_gather_priority(),
            NewCardGatherPriority::AscendingRetrievability
                | NewCardGatherPriority::DescendingRetrievability
        );
        let refresh_interval = config
            .refresh_interval
            .max(1)
            .max(config.approximate_refresh_answers);
        let mut models = HashMap::new();
        let mut cards = Vec::with_capacity(inputs.len());
        let mut estimated = 0;
        for input in inputs.iter().cloned() {
            let (card, was_estimated) = InstantCard::new(input, config.now_millis, &mut models)?;
            estimated += u32::from(was_estimated);
            cards.push(card);
        }
        let days = config.days as usize;
        // Typical answers per day: the median sampled session, capped by the
        // study deck limit. It only sizes the systematic recall error.
        let deviations = stratum.map(|stratum| {
            let answer_limit = u64::from(study_preset.inner.reviews_per_day)
                .saturating_add(if config.new_cards_ignore_review_limit {
                    u64::from(study_preset.inner.new_per_day)
                } else {
                    0
                })
                .max(1) as usize;
            let mut lengths = config
                .timing_patterns
                .iter()
                .map(Vec::len)
                .collect::<Vec<_>>();
            lengths.sort_unstable();
            let n_flat = lengths[lengths.len() / 2].min(answer_limit).max(1);
            let bias_sd = f64::from(config.stratified_bias_sd);
            let review_variance = f64::from(config.stratified_review_variance);
            let kappa = if review_variance > 0.0 && bias_sd.is_finite() {
                bias_sd * (n_flat as f64 * days as f64 / review_variance).sqrt()
            } else {
                0.0
            };
            day_deviations(
                days,
                seed,
                stratum,
                f64::from(config.stratified_scale),
                kappa,
            )
        });
        let mut output = ForecastPath {
            due: vec![0; days],
            done: vec![0; days],
            new: vec![0; days],
            lapses: vec![0; days],
            seconds: vec![0.0; days],
            group_days: (0..group_ids.len())
                .map(|_| [vec![0; days], vec![0; days], vec![0; days]])
                .collect(),
            total_cost: 0.0,
            review_count: 0,
        };
        // Card indexes in ascending order for queues 0 (new), 1 (intraday
        // learning), 2 (review) and 3 (interday learning). A card's queue only
        // changes when it is answered, so these replace full scans of `cards`.
        let mut queue_members: [Vec<usize>; 4] = Default::default();
        for (index, card) in cards.iter().enumerate() {
            if let Some(members) = queue_slot(card.input.queue).map(|q| &mut queue_members[q]) {
                members.push(index);
            }
        }
        let mut review_candidates = Vec::new();
        let mut answers = 0u32;
        let path_started = std::time::Instant::now();
        let mut times = InstantPhaseTimes::default();
        for day in 0..days {
            check_cancel()?;
            let pattern_index = (simulation_unit_hash(i64::from(seed), day as i64, 0)
                * config.timing_patterns.len() as f64) as usize;
            let pattern =
                &config.timing_patterns[pattern_index.min(config.timing_patterns.len() - 1)];
            let success_shares = config.timing_pattern_success_shares
                [pattern_index.min(config.timing_patterns.len() - 1)];
            let day_review_model = RwkvWorkloadReviewModel {
                grade_seconds: config.review_model.grade_seconds,
                bucket_probabilities: (0..20).map(|bucket| (bucket, success_shares)).collect(),
            };
            let mut short_gaps = pattern
                .windows(2)
                .map(|pair| pair[1].0 - pair[0].0)
                .filter(|gap| *gap <= 60_000)
                .collect::<Vec<_>>();
            short_gaps.sort_unstable();
            let fallback_gap = short_gaps
                .get(short_gaps.len() / 2)
                .copied()
                .unwrap_or(1_000);
            let mut due_seen = vec![false; cards.len()];
            let day_start = day as i64 * DAY_MILLIS;
            let mut millis = day_start
                + i64::from(
                    pattern[0]
                        .0
                        .max(if day == 0 { config.now_millis } else { 0 }),
                );
            let mut sample = 0usize;
            let mut refresh = true;
            let mut refresh_at = millis;
            let mut force_full_refresh = true;
            let mut precomputed: Option<usize> = None;
            let mut answers_today = 0u32;
            let mut new_answered = 0usize;
            let mut main_answered = 0usize;
            let mut answer_stratum = None;
            // Except when gathering by retrievability, new-card ordering only
            // depends on the day, seed and immutable card metadata. Answering
            // a new card removes it from this order; it never becomes new again.
            let mut new_order: Option<Vec<usize>> = None;
            while millis < day_start + DAY_MILLIS {
                if sample.is_multiple_of(128) {
                    check_cancel()?;
                }
                times.iterations += 1;
                let did_refresh = refresh;
                if did_refresh {
                    let started = std::time::Instant::now();
                    let mut indexes = if config.candidate_refresh_enabled && !force_full_refresh {
                        cards
                            .iter()
                            .enumerate()
                            .filter(|(_, card)| card.score_in_map)
                            .map(|(index, _)| index)
                            .collect::<Vec<_>>()
                    } else {
                        // Only the candidate window reads `score_in_map`.
                        if config.candidate_refresh_enabled {
                            cards.iter_mut().for_each(|card| card.score_in_map = false);
                        }
                        let mut indexes = queue_members[2].clone();
                        if score_new {
                            // Same ascending order as a scan of all cards.
                            indexes.extend_from_slice(&queue_members[0]);
                            indexes.sort_unstable();
                        }
                        indexes
                    };
                    if config.candidate_refresh_enabled && !force_full_refresh {
                        indexes.sort_by(|&a, &b| {
                            let priority = |card: &InstantCard| match config.review_order {
                                ReviewCardOrder::RetrievabilityDescending => -card.score,
                                ReviewCardOrder::RelativeOverdueness => {
                                    card.score / card.input.desired_retention.max(0.0001)
                                }
                                _ => card.score,
                            };
                            priority(&cards[a])
                                .total_cmp(&priority(&cards[b]))
                                .then_with(|| {
                                    cards[a]
                                        .input
                                        .input
                                        .review_input
                                        .card_id
                                        .cmp(&cards[b].input.input.review_input.card_id)
                                })
                        });
                        indexes.truncate(config.candidate_refresh_limit.max(1));
                    }
                    let queries = indexes
                        .iter()
                        .map(|&index| cards[index].query(day as i64, refresh_at))
                        .collect::<Vec<_>>();
                    for (index, score) in indexes
                        .into_iter()
                        .zip(self.instant_scores(&queries, &states)?)
                    {
                        cards[index].score = score;
                        cards[index].score_in_map = true;
                    }
                    force_full_refresh = false;
                    times.refreshes += 1;
                    times.refreshed_cards += queries.len() as u64;
                    times.refresh += started.elapsed();
                }
                let started = std::time::Instant::now();
                // Candidates depend on the current time and answer count, so
                // they are rebuilt for every answer, but only for the cards
                // that are read: all reviews here, others on demand below.
                let candidate = |index: usize| cards[index].candidate(day as i64, millis, answers);
                review_candidates.clear();
                review_candidates.extend(queue_members[2].iter().map(|&index| candidate(index)));
                let policy = InstantReviewPolicy {
                    study_deck_id: DeckId(config.study_deck_id),
                    same_day_allowed: config.same_day_allowed,
                    min_intervening: config.min_intervening_reviews,
                    min_elapsed_seconds: config.min_elapsed_secs,
                    order: config.review_order,
                    shuffle: i64::from(answers) ^ i64::from(seed),
                    day: day as i64,
                };
                for (&index, candidate) in queue_members[2].iter().zip(&review_candidates) {
                    if !due_seen[index] && instant_due(candidate, policy) {
                        due_seen[index] = true;
                        let group = group_ids
                            .binary_search(&candidate.deck_id)
                            .expect("group is listed");
                        output.due[day] += 1;
                        output.group_days[group][0][day] += 1;
                    }
                }
                times.candidates += started.elapsed();
                let started = std::time::Instant::now();
                let mut day_learning = queue_members[3]
                    .iter()
                    .copied()
                    .filter(|&index| cards[index].learning_due_millis <= day_start)
                    .collect::<Vec<_>>();
                day_learning.sort_by_key(|&index| {
                    (
                        cards[index].learning_due_millis,
                        cards[index].input.input.review_input.card_id,
                    )
                });
                let reviews = &queue_members[2];
                let mut new = if let Some(order) = &new_order {
                    order
                        .iter()
                        .copied()
                        .filter(|&index| cards[index].input.queue == 0)
                        .collect::<Vec<_>>()
                } else {
                    queue_members[0].clone()
                };
                let gather = study_preset.inner.new_card_gather_priority();
                if new_order.is_none() {
                    new.sort_by(|&a, &b| {
                        let a = &cards[a];
                        let b = &cards[b];
                        let position = || a.input.new_position.cmp(&b.input.new_position);
                        let deck = || {
                            deck_order
                                .get(&a.input.group)
                                .cmp(&deck_order.get(&b.input.group))
                        };
                        let random = |card: &InstantCard, note: bool| {
                            let input = &card.input.input.review_input;
                            let id = if note {
                                input.note_id.unwrap_or(input.card_id)
                            } else {
                                input.card_id
                            };
                            simulation_unit_hash(id, day as i64, i64::from(seed))
                        };
                        let order = match gather {
                            NewCardGatherPriority::Deck => deck().then_with(position),
                            NewCardGatherPriority::DeckThenRandomNotes => {
                                deck().then_with(|| random(a, true).total_cmp(&random(b, true)))
                            }
                            NewCardGatherPriority::LowestPosition => position(),
                            NewCardGatherPriority::HighestPosition => position().reverse(),
                            NewCardGatherPriority::RandomNotes => {
                                random(a, true).total_cmp(&random(b, true))
                            }
                            NewCardGatherPriority::RandomCards => {
                                random(a, false).total_cmp(&random(b, false))
                            }
                            NewCardGatherPriority::AscendingRetrievability => {
                                a.score.total_cmp(&b.score).then_with(position)
                            }
                            NewCardGatherPriority::DescendingRetrievability => {
                                b.score.total_cmp(&a.score).then_with(position)
                            }
                        };
                        order.then_with(|| {
                            a.input
                                .input
                                .review_input
                                .card_id
                                .cmp(&b.input.input.review_input.card_id)
                        })
                    });
                    if !score_new {
                        new_order = Some(new.clone());
                    }
                }
                times.lists += started.elapsed();
                let started = std::time::Instant::now();
                let queues = instant_study_candidates(
                    &review_candidates,
                    &day_learning
                        .iter()
                        .map(|&index| candidate(index))
                        .collect::<Vec<_>>(),
                    &new.iter()
                        .map(|&index| cards[index].input.group)
                        .collect::<Vec<_>>(),
                    precomputed.map(|index| (cards[index].input.queue, candidate(index))),
                    &decks,
                    &deck_configs,
                    config.today.saturating_add(day as u32),
                    config.new_cards_ignore_review_limit,
                    policy,
                )?;
                times.select += started.elapsed();
                let started = std::time::Instant::now();
                let eligible_learning = |index: usize, min_intervening| {
                    let candidate = candidate(index);
                    rwkv_repeat_spacing_eligibility(
                        candidate.elapsed_seconds,
                        min_intervening,
                        config.min_elapsed_secs,
                        candidate.intervening_reviews,
                    ) == RwkvReviewScoreEligibility::Eligible
                };
                let reserved_day_learning = queues
                    .learning
                    .into_iter()
                    .map(|index| day_learning[index])
                    .collect::<Vec<_>>();
                let day_queue = reserved_day_learning
                    .iter()
                    .copied()
                    .filter(|&index| eligible_learning(index, config.min_intervening_reviews))
                    .collect::<Vec<_>>();
                let review_queue = queues
                    .review
                    .into_iter()
                    .map(|index| reviews[index])
                    .collect::<Vec<_>>();
                let new_queue = queues
                    .new
                    .into_iter()
                    .map(|index| new[index])
                    .collect::<Vec<_>>();
                let main = mix_queues(
                    review_queue,
                    day_queue,
                    study_preset.inner.interday_learning_mix(),
                    0,
                    0,
                );
                let main = mix_queues(
                    main,
                    new_queue,
                    study_preset.inner.new_mix(),
                    main_answered,
                    new_answered,
                );
                let mut intraday = queue_members[1]
                    .iter()
                    .copied()
                    .filter(|&index| {
                        cards[index].learning_due_millis <= millis
                            && eligible_learning(index, config.min_intervening_reviews)
                    })
                    .collect::<Vec<_>>();
                intraday.sort_by_key(|&index| {
                    (
                        cards[index].reps,
                        cards[index].learning_due_millis,
                        cards[index].input.input.review_input.card_id,
                    )
                });
                let mut queue =
                    mix_queues(main, intraday, study_preset.intraday_learning_mix(), 0, 0);
                if !queues.precomputed_allowed {
                    precomputed = None;
                }
                if queue.is_empty() && precomputed.is_none() && !did_refresh {
                    refresh = true;
                    refresh_at = millis;
                    continue;
                }
                if queue.is_empty() && precomputed.is_none() {
                    // Anki can relax the intervening-review minimum when only
                    // learning remains, and may show a card within learn-ahead.
                    let mut learning = cards
                        .iter()
                        .enumerate()
                        .filter(|(index, card)| {
                            (card.input.queue == 1 || reserved_day_learning.contains(index))
                                && card.learning_due_millis
                                    <= millis + i64::from(config.learn_ahead_secs) * 1000
                                && eligible_learning(*index, 0)
                        })
                        .map(|(index, _)| index)
                        .collect::<Vec<_>>();
                    learning.sort_by_key(|&index| {
                        (
                            cards[index].learning_due_millis,
                            cards[index].input.input.review_input.card_id,
                        )
                    });
                    queue = learning;
                }
                times.lists += started.elapsed();
                let started = std::time::Instant::now();
                if answer_stratum.is_none() {
                    if let Some(deviations) = &deviations {
                        let preview = precomputed
                            .into_iter()
                            .chain(
                                queue
                                    .iter()
                                    .copied()
                                    .filter(|index| Some(*index) != precomputed),
                            )
                            .take(PREVIEW_LIMIT)
                            .map(|index| cards[index].query(day as i64, millis))
                            .collect::<Vec<_>>();
                        if !preview.is_empty() {
                            let recalls = self.instant_scores(&preview, &states)?;
                            let expected_answers = queue.len()
                                + usize::from(
                                    precomputed.is_some_and(|index| !queue.contains(&index)),
                                );
                            answer_stratum = Some(AnswerStratum::new(
                                deviations[day],
                                expected_answers,
                                &recalls,
                            ));
                        }
                    }
                }
                times.preview += started.elapsed();
                if let Some(index) = precomputed.take().or_else(|| queue.first().copied()) {
                    if config.precompute_next_card {
                        precomputed = queue.iter().copied().find(|&candidate| candidate != index);
                    }
                    let answered = candidate(index);
                    let group_index = group_ids
                        .binary_search(&answered.deck_id)
                        .expect("group is listed");
                    let queue_kind = cards[index].input.queue;
                    if queue_kind == 2 && !due_seen[index] {
                        due_seen[index] = true;
                        output.due[day] += 1;
                        output.group_days[group_index][0][day] += 1;
                    }
                    let exempt = matches!(queue_kind, 2 | 3)
                        && ignores_review_limit(&answered, &decks, &deck_configs)?;
                    let card = &mut cards[index];
                    let reviewed_today = card.last_day == Some(day as i64);
                    // The queue can intentionally retain stale scores between
                    // refreshes. Recall at the actual answer time still uses
                    // the current recurrent state, not the stale queue score.
                    let started = std::time::Instant::now();
                    let model_recall = self
                        .instant_scores(&[card.query(day as i64, millis)], &states)?[0]
                        .clamp(0.0, 1.0);
                    let recall = (model_recall + recall_offset).clamp(0.0, 1.0);
                    times.answer_score += started.elapsed();
                    let started = std::time::Instant::now();
                    let answer_recall = answer_stratum.as_ref().map_or(recall, |stratum| {
                        stratum.probability(recall, queue.len().max(1))
                    });
                    let ease = paired_grade(
                        &day_review_model,
                        answer_recall,
                        card.input.input.review_input.card_id,
                        card.reps,
                        seed,
                    );
                    if let Some(stratum) = &mut answer_stratum {
                        stratum.observe(recall, ease != 1);
                    }
                    let sampled_duration = pattern[sample % pattern.len()].1;
                    let duration = if queue_kind == 2 {
                        config
                            .review_time_model
                            .as_ref()
                            .map_or(sampled_duration, |model| {
                                model.duration_millis(sampled_duration, model_recall, ease)
                            })
                    } else {
                        sampled_duration
                    };
                    let mut answer = card.query(day as i64, millis);
                    answer.is_query = false;
                    answer.ease = Some(ease);
                    answer.duration_millis = Some(i64::from(duration));
                    if queue_kind == 0 {
                        answer.current_elapsed_days = Some(-1);
                        answer.current_elapsed_seconds = Some(-1);
                    } else if queue_kind == 2 && reviewed_today {
                        if let Some((kind, previous_grade)) = card.last_kind_and_grade {
                            answer.card_type = Some(match kind {
                                1 if previous_grade == 1 => 3,
                                2 if previous_grade <= 2 => 3,
                                _ => 4,
                            });
                        }
                    }
                    let preset = decks
                        .iter()
                        .find(|deck| deck.id.0 == card.input.group)
                        .and_then(|deck| deck.config_id())
                        .and_then(|id| deck_configs.get(&id))
                        .ok_or_else(|| {
                            io::Error::new(
                                io::ErrorKind::InvalidInput,
                                "forecast card's preset is missing",
                            )
                        })?;
                    card.answer(
                        day as i64,
                        millis,
                        i64::from(ease),
                        preset,
                        config.fsrs_learning_queues_disabled,
                        config.fsrs_short_term_with_steps_enabled,
                        seed,
                    )?;
                    self.apply_simulation_answer(&answer, &mut states)?;
                    answers += 1;
                    answers_today += 1;
                    match queue_kind {
                        0 => {
                            new_answered += 1;
                            output.new[day] += 1;
                            output.group_days[group_index][2][day] += 1;
                        }
                        2 => {
                            main_answered += 1;
                            output.review_count += 1;
                            output.done[day] += 1;
                            output.group_days[group_index][1][day] += 1;
                            if ease == 1 && !reviewed_today {
                                output.lapses[day] += 1;
                            }
                        }
                        3 => main_answered += 1,
                        _ => {}
                    }
                    output.seconds[day] += duration as f32 / 1000.0;
                    output.total_cost += duration as f32 / 1000.0;
                    if queue_kind == 0 || (matches!(queue_kind, 2 | 3) && !exempt) {
                        increment_study_counts(
                            &mut decks,
                            card.input.group,
                            config.today.saturating_add(day as u32),
                            queue_kind == 0,
                        )?;
                    }
                    card.last_day = Some(day as i64);
                    card.last_millis = Some(millis);
                    card.last_answer_sequence = Some(answers);
                    card.reps += 1;
                    if card.input.queue != queue_kind {
                        if let Some(old) = queue_slot(queue_kind) {
                            let members = &mut queue_members[old];
                            if let Ok(at) = members.binary_search(&index) {
                                members.remove(at);
                            }
                        }
                        if let Some(new) = queue_slot(card.input.queue) {
                            let members = &mut queue_members[new];
                            if let Err(at) = members.binary_search(&index) {
                                members.insert(at, index);
                            }
                        }
                    }
                    times.apply += started.elapsed();
                    let started = std::time::Instant::now();
                    refresh = answers_today.is_multiple_of(refresh_interval);
                    if card.input.queue == -1 {
                        card.score_in_map = false;
                    } else {
                        // Without a candidate window, scores are only read for
                        // reviews (and new cards when gathering by
                        // retrievability), and a full refresh rescores all of
                        // them before the next selection reads any score. The
                        // next day also starts with a full refresh.
                        let scored_queue =
                            card.input.queue == 2 || (score_new && card.input.queue == 0);
                        let rescored_before_read =
                            !config.candidate_refresh_enabled && (refresh || !scored_queue);
                        if !rescored_before_read {
                            let query = card.query(day as i64, millis);
                            card.score = self.instant_scores(&[query], &states)?[0];
                        }
                        card.score_in_map = true;
                    }
                    times.post_score += started.elapsed();
                    if refresh {
                        refresh_at = millis;
                    }
                } else {
                    // With no card to answer, the current study session ends.
                    // Resume at the next observed session, rather than
                    // inventing score refreshes for each skipped answer.
                    let next_session = next_recorded_session(pattern, sample, millis - day_start);
                    let Some(next_session) = next_session else {
                        break;
                    };
                    sample = next_session;
                    millis = day_start + i64::from(pattern[sample].0);
                    refresh = true;
                    refresh_at = millis;
                    force_full_refresh = false;
                    answers_today = 0;
                    new_answered = 0;
                    main_answered = 0;
                    continue;
                }
                let gap = if sample + 1 < pattern.len() {
                    pattern[sample + 1].0 - pattern[sample].0
                } else {
                    fallback_gap
                };
                millis += i64::from(gap.max(1));
                sample += 1;
            }
            if let (Some(answer_stratum), Some(path)) = (&answer_stratum, stratum) {
                answer_stratum.log_summary(path, day);
            }
            report_day(
                day as u32,
                [
                    output.due[day] as f32,
                    output.done[day] as f32,
                    output.new[day] as f32,
                    output.lapses[day] as f32,
                    output.seconds[day],
                ],
            )?;
        }
        times.log(seed, stratum, answers, path_started.elapsed());
        Ok((output, estimated))
    }

    fn instant_scores(
        &mut self,
        inputs: &[ReviewInput],
        states: &SimulationStates<'_>,
    ) -> io::Result<Vec<f32>> {
        let views = inputs
            .iter()
            .map(|input| states.state_view(input))
            .collect::<Vec<_>>();
        let work_items = inputs
            .iter()
            .zip(&views)
            .map(|(input, view)| ReviewPredictionBorrowedWorkItem {
                features: self.features.features_for(input),
                state: view.as_ref(),
            })
            .collect::<Vec<_>>();
        self.model.review_retrievability_many_borrowed(&work_items)
    }
}

/// The `queue_members` slot of an Instant forecast queue, if it is tracked.
fn queue_slot(queue: i32) -> Option<usize> {
    usize::try_from(queue).ok().filter(|queue| *queue < 4)
}

fn mix_queues(
    first: Vec<usize>,
    second: Vec<usize>,
    mode: ReviewMix,
    first_answered: usize,
    second_answered: usize,
) -> Vec<usize> {
    match mode {
        ReviewMix::BeforeReviews => second.into_iter().chain(first).collect(),
        ReviewMix::AfterReviews => first.into_iter().chain(second).collect(),
        ReviewMix::MixWithReviews => Intersperser::with_progress(
            first.into_iter(),
            second.into_iter(),
            first_answered,
            second_answered,
        )
        .collect(),
    }
}

fn next_recorded_session(pattern: &[(u32, u32)], sample: usize, elapsed: i64) -> Option<usize> {
    ((sample + 1)..pattern.len()).find(|&index| {
        pattern[index].0 - pattern[index - 1].0 >= SESSION_BREAK_MILLIS
            && i64::from(pattern[index].0) > elapsed
    })
}

fn increment_study_counts(
    decks: &mut [Deck],
    deck_id: i64,
    today: u32,
    new: bool,
) -> io::Result<()> {
    let name = decks
        .iter()
        .find(|deck| deck.id.0 == deck_id)
        .ok_or_else(|| {
            io::Error::new(
                io::ErrorKind::InvalidInput,
                "forecast card's deck is missing",
            )
        })?
        .name
        .as_native_str()
        .to_owned();
    for deck in decks {
        let ancestor = deck.name.as_native_str();
        if name == ancestor
            || name
                .strip_prefix(ancestor)
                .is_some_and(|suffix| suffix.starts_with('\u{1f}'))
        {
            let common = &mut deck.common;
            if common.last_day_studied != today {
                common.last_day_studied = today;
                common.new_studied = 0;
                common.review_studied = 0;
                common.learning_studied = 0;
                common.milliseconds_studied = 0;
            }
            if new {
                common.new_studied = common.new_studied.saturating_add(1);
            } else {
                common.review_studied = common.review_studied.saturating_add(1);
            }
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn review_duration_uses_recall_and_grade_with_historical_variation() {
        let good = RwkvInstantReviewTimeGrade {
            intercept: 2.0,
            slope: 20.0,
            min_recall: 0.1,
            max_recall: 0.9,
            fallback_seconds: 12.0,
        };
        let model = RwkvInstantReviewTimeModel {
            reference_seconds: 10.0,
            grades: [None, None, Some(good), None],
        };
        assert!(model.valid());
        assert_eq!(model.duration_millis(10_000, 0.9, 3), 4_000);
        assert_eq!(model.duration_millis(10_000, 0.4, 3), 14_000);
        assert_eq!(model.duration_millis(5_000, 0.4, 3), 7_000);
        // Do not extrapolate outside the R values in the training history.
        assert_eq!(model.duration_millis(10_000, 1.0, 3), 4_000);
        assert_eq!(model.duration_millis(10_000, 0.4, 1), 10_000);
        assert_eq!(model.duration_millis(10_000, f32::NAN, 3), 10_000);
    }

    fn input(card_type: i64, queue: i32, fsrs_enabled: bool) -> RwkvInstantForecastCard {
        RwkvInstantForecastCard {
            input: RwkvWorkloadSimulationInput {
                review_input: ReviewInput {
                    card_id: 123,
                    note_id: Some(10),
                    deck_id: Some(1),
                    preset_id: Some(1),
                    is_query: true,
                    ease: None,
                    duration_millis: None,
                    card_type: Some(card_type),
                    day_offset: Some(100),
                    current_elapsed_days: (card_type != 0).then_some(10),
                    current_elapsed_seconds: (card_type != 0).then_some(864_000),
                    target_retentions: [Some(0.9); 4],
                    enforce_grade_order: true,
                },
                interval_days: Some(10),
                ease_factor: Some(2500),
                reps: Some(3),
                lapses: Some(0),
            },
            due_day: 0,
            group: 1,
            desired_retention: 0.9,
            instant_enabled: true,
            intervening_reviews: None,
            last_kind_and_grade: None,
            queue,
            learning_due_millis: 0,
            remaining_steps: 0,
            new_position: 0,
            fsrs_memory: None,
            fsrs_parameters: vec![],
            fsrs_historical_retention: 0.9,
            fsrs_enabled,
        }
    }

    #[test]
    fn new_cards_follow_the_configured_learning_steps() {
        let mut preset = DeckConfig::default();
        preset.inner.learn_steps = vec![1.0, 10.0];
        let (mut card, _) = InstantCard::new(input(0, 0, false), 0, &mut HashMap::new()).unwrap();
        card.answer(0, 0, 3, &preset, false, true, 0).unwrap();
        assert_eq!(card.input.queue, 1);
        assert_eq!(card.learning_due_millis, 600_000);
        card.last_day = Some(0);
        card.last_millis = Some(0);
        card.answer(0, 600_000, 3, &preset, false, true, 0).unwrap();
        assert_eq!(card.input.queue, 2);
        assert!(card.input.due_day >= 1);
    }

    #[test]
    fn a_failed_review_enters_relearning() {
        let mut preset = DeckConfig::default();
        preset.inner.relearn_steps = vec![10.0];
        let (mut card, _) = InstantCard::new(input(2, 2, false), 0, &mut HashMap::new()).unwrap();
        card.answer(0, 0, 1, &preset, false, true, 0).unwrap();
        assert!(matches!(
            card.state,
            CardState::Normal(NormalState::Relearning(_))
        ));
        assert_eq!(card.input.queue, 1);
        assert_eq!(card.learning_due_millis, 600_000);
    }

    #[test]
    fn a_leech_is_suspended_when_the_preset_requests_it() {
        let mut preset = DeckConfig::default();
        preset.inner.leech_threshold = 8;
        preset.inner.leech_action = LeechAction::Suspend as i32;
        let mut card_input = input(2, 2, false);
        card_input.input.lapses = Some(7);
        let (mut card, _) = InstantCard::new(card_input, 0, &mut HashMap::new()).unwrap();
        card.answer(0, 0, 1, &preset, false, true, 0).unwrap();
        assert_eq!(card.input.queue, -1);
    }

    #[test]
    fn fsrs_new_cards_acquire_a_memory_state_during_learning() {
        let mut preset = DeckConfig::default();
        preset.inner.learn_steps = vec![1.0, 10.0];
        let (mut card, _) = InstantCard::new(input(0, 0, true), 0, &mut HashMap::new()).unwrap();
        card.answer(0, 0, 3, &preset, false, true, 0).unwrap();
        match card.state {
            CardState::Normal(NormalState::Learning(learning)) => {
                assert!(learning.memory_state.is_some())
            }
            _ => panic!("expected a learning card"),
        }
    }

    #[test]
    fn a_new_session_requires_a_recorded_break_and_a_future_timestamp() {
        let pattern = [(1000, 100), (2000, 100), (1_802_000, 100), (1_803_000, 100)];
        assert_eq!(next_recorded_session(&pattern, 0, 1000), Some(2));
        assert_eq!(next_recorded_session(&pattern, 0, 1_802_000), None);
        assert_eq!(next_recorded_session(&pattern, 2, 1_802_000), None);
    }
}
