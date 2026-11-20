// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod answering;
mod states;

use std::collections::HashMap;
use std::sync::Arc;

use anki_proto::cards;
use anki_proto::generic;
use anki_proto::scheduler;
use anki_proto::scheduler::ComputeFsrsParamsResponse;
use anki_proto::scheduler::ComputeMemoryStateResponse;
use anki_proto::scheduler::ComputeOptimalRetentionResponse;
use anki_proto::scheduler::FsrsBenchmarkResponse;
use anki_proto::scheduler::FsrsCurrentRetrievabilityRequest;
use anki_proto::scheduler::FsrsCurrentRetrievabilityResponse;
use anki_proto::scheduler::FsrsDesiredRetentionForIntervalsBatchRequest;
use anki_proto::scheduler::FsrsDesiredRetentionForIntervalsBatchResponse;
use anki_proto::scheduler::FsrsIntervalAtRetrievabilityBatchRequest;
use anki_proto::scheduler::FsrsIntervalAtRetrievabilityBatchResponse;
use anki_proto::scheduler::FsrsIntervalAtRetrievabilityByConfigBatchRequest;
use anki_proto::scheduler::FsrsIntervalAtRetrievabilityByConfigBatchResponse;
use anki_proto::scheduler::FsrsIntervalAtRetrievabilityResponse;
use anki_proto::scheduler::FsrsIntervalAtRetrievabilityVariableBatchRequest;
use anki_proto::scheduler::FsrsIntervalAtRetrievabilityVariableBatchResponse;
use anki_proto::scheduler::FsrsNextIntervalRequest;
use anki_proto::scheduler::FsrsNextIntervalResponse;
use anki_proto::scheduler::FsrsPresetForCardResponse;
use anki_proto::scheduler::FsrsPresetIdsForCardsResponse;
use anki_proto::scheduler::FuzzDeltaRequest;
use anki_proto::scheduler::FuzzDeltaResponse;
use anki_proto::scheduler::GetOptimalRetentionParametersResponse;
use anki_proto::scheduler::PruneRwkvReviewRetrievabilityCacheRowsRequest;
use anki_proto::scheduler::RwkvAnsweredCardQueueScorePatchRequest;
use anki_proto::scheduler::RwkvCardInfoScoreRequest;
use anki_proto::scheduler::RwkvHistoricalReviewFingerprintRequest;
use anki_proto::scheduler::RwkvHistoricalReviewFingerprintResponse;
use anki_proto::scheduler::RwkvRetrievabilityScoreResponse;
use anki_proto::scheduler::RwkvReviewInputRowsForCardsRequest;
use anki_proto::scheduler::RwkvReviewInputRowsForCardsResponse;
use anki_proto::scheduler::RwkvReviewInputRowsForDeckReviewQueueRequest;
use anki_proto::scheduler::RwkvReviewInputRowsForSearchRequest;
use anki_proto::scheduler::RwkvReviewQueueInterveningReviewsRequest;
use anki_proto::scheduler::RwkvReviewQueueScoresRequest;
use anki_proto::scheduler::RwkvReviewRescheduleRequest;
use anki_proto::scheduler::RwkvReviewRetrievabilityCacheRowsRequest;
use anki_proto::scheduler::RwkvStatsGraphScoresRequest;
use anki_proto::scheduler::SchedulingStatesWithIntervalsRequest;
use anki_proto::scheduler::SimulateFsrsReviewRequest;
use anki_proto::scheduler::SimulateFsrsReviewResponse;
use anki_proto::scheduler::SimulateFsrsWorkloadResponse;
use fsrs::benchmark;
use fsrs::compute_parameters;
use fsrs::ComputeParametersInput;
use fsrs::ComputeParametersVersion;
use fsrs::FSRSItem;
use fsrs::FSRSReview;
use fsrs::FSRS;

use crate::backend::Backend;
use crate::collection::RwkvReviewQueueScoreEntry;
use crate::collection::RwkvStatsGraphScoreEntry;
use crate::config::BoolKey;
use crate::deckconfig::fsrs7_enable_scheduling_penalties;
use crate::deckconfig::fsrs7_optimize_include_same_day_reviews;
use crate::deckconfig::FsrsVersion;
use crate::prelude::*;
use crate::scheduler::answering::PreviewDelays;
use crate::scheduler::fsrs::batch::ComputeParamsBatchInput;
use crate::scheduler::fsrs::memory_state::fsrs_memory_state_for_params;
use crate::scheduler::fsrs::params::compute_fsrs_review_retrievability_calibration_rows;
use crate::scheduler::fsrs::params::ignore_revlogs_before_ms_from_config;
use crate::scheduler::fsrs::params::ComputeParamsRequest;
use crate::scheduler::fsrs::params::DynamicDesiredRetentionSimulatorOptions;
use crate::scheduler::fsrs::params::FsrsReviewPredictionContext;
use crate::scheduler::fsrs::params::PrepareComputeParamsInput;
use crate::scheduler::fsrs::preset::FsrsPreset;
use crate::scheduler::fsrs::preset::FsrsPresetId;
use crate::scheduler::fsrs::uses_fractional_intervals;
use crate::scheduler::new::NewCardDueOrder;
use crate::scheduler::rwkv::RwkvReviewRescheduleItem;
use crate::scheduler::states::CardState;
use crate::scheduler::states::LearnState;
use crate::scheduler::states::SchedulingStates;
use crate::search::SearchNode;
use crate::search::SortMode;
use crate::search::TryIntoSearch;
use crate::stats::studied_today;
use crate::storage::FsrsReviewRetrievabilityCacheRow;
use crate::storage::FsrsReviewRetrievabilitySampleRole;
use crate::storage::RwkvReviewRetrievabilityCacheRow;
use crate::storage::RwkvReviewRetrievabilitySampleRole;

fn rwkv_score_entries(
    scores: Vec<scheduler::rwkv_review_queue_scores_request::Score>,
) -> Result<HashMap<CardId, RwkvReviewQueueScoreEntry>> {
    let mut entries = HashMap::with_capacity(scores.len());
    for score in scores {
        entries.insert(score.card_id.into(), rwkv_score_entry(score)?);
    }
    Ok(entries)
}

fn rwkv_score_entry(
    score: scheduler::rwkv_review_queue_scores_request::Score,
) -> Result<RwkvReviewQueueScoreEntry> {
    require!(
        score.retrievability.is_finite() && (0.0..=1.0).contains(&score.retrievability),
        "invalid RWKV retrievability"
    );
    if let Some(target_retention) = score.target_retention {
        require!(
            target_retention.is_finite() && (0.0..=1.0).contains(&target_retention),
            "invalid RWKV target retention"
        );
    }
    Ok(RwkvReviewQueueScoreEntry {
        retrievability: score.retrievability,
        intervening_reviews: score.intervening_reviews,
        target_retention: score.target_retention,
    })
}

impl crate::services::SchedulerService for Collection {
    /// This behaves like _updateCutoff() in older code - it also unburies at
    /// the start of a new day.
    fn sched_timing_today(&mut self) -> Result<scheduler::SchedTimingTodayResponse> {
        let timing = self.timing_today()?;
        self.unbury_if_day_rolled_over(timing)?;
        Ok(timing.into())
    }

    /// Fetch data from DB and return rendered string.
    fn studied_today(&mut self) -> Result<generic::String> {
        self.studied_today().map(Into::into)
    }

    /// Message rendering only, for old graphs.
    fn studied_today_message(
        &mut self,
        input: scheduler::StudiedTodayMessageRequest,
    ) -> Result<generic::String> {
        Ok(studied_today(input.cards, input.seconds as f32, &self.tr).into())
    }

    fn update_stats(&mut self, input: scheduler::UpdateStatsRequest) -> Result<()> {
        self.transact_no_undo(|col| {
            let today = col.current_due_day(0)?;
            let usn = col.usn()?;
            col.update_deck_stats(today, usn, input)
        })
    }

    fn extend_limits(&mut self, input: scheduler::ExtendLimitsRequest) -> Result<()> {
        self.transact_no_undo(|col| {
            let today = col.current_due_day(0)?;
            let usn = col.usn()?;
            col.extend_limits(
                today,
                usn,
                input.deck_id.into(),
                input.new_delta,
                input.review_delta,
            )
        })
    }

    fn counts_for_deck_today(
        &mut self,
        input: anki_proto::decks::DeckId,
    ) -> Result<scheduler::CountsForDeckTodayResponse> {
        self.counts_for_deck_today(input.did.into())
    }

    fn congrats_info(&mut self) -> Result<scheduler::CongratsInfoResponse> {
        self.congrats_info()
    }

    fn restore_buried_and_suspended_cards(
        &mut self,
        input: anki_proto::cards::CardIds,
    ) -> Result<anki_proto::collection::OpChanges> {
        let cids: Vec<_> = input.cids.into_iter().map(CardId).collect();
        self.unbury_or_unsuspend_cards(&cids).map(Into::into)
    }

    fn unbury_deck(
        &mut self,
        input: scheduler::UnburyDeckRequest,
    ) -> Result<anki_proto::collection::OpChanges> {
        self.unbury_deck(input.deck_id.into(), input.mode())
            .map(Into::into)
    }

    fn bury_or_suspend_cards(
        &mut self,
        input: scheduler::BuryOrSuspendCardsRequest,
    ) -> Result<anki_proto::collection::OpChangesWithCount> {
        let mode = input.mode();
        let cids = if input.card_ids.is_empty() {
            self.storage
                .card_ids_of_notes(&input.note_ids.into_newtype(NoteId))?
        } else {
            input.card_ids.into_newtype(CardId)
        };
        self.bury_or_suspend_cards(&cids, mode).map(Into::into)
    }

    fn empty_filtered_deck(
        &mut self,
        input: anki_proto::decks::DeckId,
    ) -> Result<anki_proto::collection::OpChanges> {
        self.empty_filtered_deck(input.did.into()).map(Into::into)
    }

    fn rebuild_filtered_deck(
        &mut self,
        input: anki_proto::decks::DeckId,
    ) -> Result<anki_proto::collection::OpChangesWithCount> {
        self.rebuild_filtered_deck(input.did.into()).map(Into::into)
    }

    fn schedule_cards_as_new(
        &mut self,
        input: scheduler::ScheduleCardsAsNewRequest,
    ) -> Result<anki_proto::collection::OpChanges> {
        let cids = input.card_ids.into_newtype(CardId);
        self.reschedule_cards_as_new(
            &cids,
            input.log,
            input.restore_position,
            input.reset_counts,
            input
                .context
                .and_then(|s| scheduler::schedule_cards_as_new_request::Context::try_from(s).ok()),
        )
        .map(Into::into)
    }

    fn schedule_cards_as_new_defaults(
        &mut self,
        input: scheduler::ScheduleCardsAsNewDefaultsRequest,
    ) -> Result<scheduler::ScheduleCardsAsNewDefaultsResponse> {
        Ok(Collection::reschedule_cards_as_new_defaults(
            self,
            input.context(),
        ))
    }

    fn set_due_date(
        &mut self,
        input: scheduler::SetDueDateRequest,
    ) -> Result<anki_proto::collection::OpChanges> {
        let config = input.config_key.map(|v| v.key().into());
        let days = input.days;
        let cids = input.card_ids.into_newtype(CardId);
        self.set_due_date(&cids, &days, config).map(Into::into)
    }

    fn grade_now(
        &mut self,
        input: scheduler::GradeNowRequest,
    ) -> Result<anki_proto::collection::OpChanges> {
        self.grade_now(input).map(Into::into)
    }

    fn sort_cards(
        &mut self,
        input: scheduler::SortCardsRequest,
    ) -> Result<anki_proto::collection::OpChangesWithCount> {
        let cids = input.card_ids.into_newtype(CardId);
        let (start, step, random, shift) = (
            input.starting_from,
            input.step_size,
            input.randomize,
            input.shift_existing,
        );
        let order = if random {
            NewCardDueOrder::Random
        } else {
            NewCardDueOrder::Preserve
        };

        self.sort_cards(&cids, start, step, order, shift)
            .map(Into::into)
    }

    fn reposition_defaults(&mut self) -> Result<scheduler::RepositionDefaultsResponse> {
        Ok(Collection::reposition_defaults(self))
    }

    fn sort_deck(
        &mut self,
        input: scheduler::SortDeckRequest,
    ) -> Result<anki_proto::collection::OpChangesWithCount> {
        self.sort_deck_legacy(input.deck_id.into(), input.randomize)
            .map(Into::into)
    }

    fn get_scheduling_states(
        &mut self,
        input: anki_proto::cards::CardId,
    ) -> Result<scheduler::SchedulingStates> {
        let cid: CardId = input.into();
        self.get_scheduling_states(cid).map(Into::into)
    }

    fn get_scheduling_states_with_opts(
        &mut self,
        input: scheduler::GetSchedulingStatesRequest,
    ) -> Result<scheduler::SchedulingStates> {
        self.get_scheduling_states_with_desired_retention_override(
            CardId(input.card_id),
            input.desired_retention_override,
        )
        .map(Into::into)
    }

    fn describe_next_states(
        &mut self,
        input: scheduler::SchedulingStates,
    ) -> Result<generic::StringList> {
        let states: SchedulingStates = input.into();
        self.describe_next_states(&states).map(Into::into)
    }

    fn state_is_leech(&mut self, input: scheduler::SchedulingState) -> Result<generic::Bool> {
        let state: CardState = input.into();
        Ok(state.leeched().into())
    }

    fn answer_card(
        &mut self,
        input: scheduler::CardAnswer,
    ) -> Result<anki_proto::collection::OpChanges> {
        let card_id = CardId(input.card_id);
        let mod_before = self.storage.get_collection_timestamps()?.collection_change;
        let reviews_before = self
            .state
            .rwkv_offline
            .as_ref()
            .and(self.state.card_queues.as_ref())
            .map(|queue| queue.counts_snapshot().review);
        let output = self.answer_card(&mut input.into())?;
        let queue_exhausted = reviews_before.is_some_and(|reviews_before| {
            self.state.card_queues.as_ref().is_some_and(|queue| {
                let counts = queue.counts_snapshot();
                (reviews_before > 0 && counts.review == 0)
                    || (counts.new == 0 && counts.learning == 0 && counts.review == 0)
            })
        });
        self.rwkv_offline_after_answer(card_id, mod_before, queue_exhausted);
        Ok(output.into())
    }

    fn upgrade_scheduler(&mut self) -> Result<()> {
        self.transact_no_undo(|col| col.upgrade_to_v2_scheduler())
    }

    fn get_queued_cards(
        &mut self,
        input: scheduler::GetQueuedCardsRequest,
    ) -> Result<scheduler::QueuedCards> {
        self.get_queued_cards(
            input.fetch_limit as usize,
            input.intraday_learning_only,
            input.skip_scheduling_states,
        )
        .map(Into::into)
    }

    fn get_deck_queue(&mut self, input: anki_proto::decks::DeckId) -> Result<scheduler::DeckQueue> {
        let queue = self.deck_queue(DeckId(input.did))?;
        Ok(scheduler::DeckQueue {
            card_ids: queue.iter().map(|(id, _)| id.0).collect(),
            queues: queue
                .iter()
                .map(|(_, kind)| scheduler::queued_cards::Queue::from(*kind) as i32)
                .collect(),
        })
    }

    fn rebuild_queued_cards_preserving_current_card(
        &mut self,
        input: cards::CardId,
    ) -> Result<scheduler::QueuedCards> {
        Collection::rebuild_queued_cards_preserving_current_card(self, input.into()).map(Into::into)
    }

    fn custom_study(
        &mut self,
        input: scheduler::CustomStudyRequest,
    ) -> Result<anki_proto::collection::OpChanges> {
        self.custom_study(input).map(Into::into)
    }

    fn custom_study_defaults(
        &mut self,
        input: scheduler::CustomStudyDefaultsRequest,
    ) -> Result<scheduler::CustomStudyDefaultsResponse> {
        self.custom_study_defaults(input.deck_id.into())
    }

    fn compute_fsrs_params(
        &mut self,
        input: scheduler::ComputeFsrsParamsRequest,
    ) -> Result<scheduler::ComputeFsrsParamsResponse> {
        self.compute_params(ComputeParamsRequest {
            search: &input.search,
            ignore_revlogs_before_ms: input.ignore_revlogs_before_ms.into(),
            current_preset: 1,
            total_presets: 1,
            current_params: &input.current_params,
            num_of_relearning_steps: input.num_of_relearning_steps as usize,
            health_check: input.health_check,
            include_same_day_reviews: input.include_same_day_reviews,
            enable_scheduling_penalties: input.enable_scheduling_penalties.unwrap_or(false),
            model_version_override: input.fsrs_version.map(health_check_model_version),
            dynamic_desired_retention_enabled: input.dynamic_desired_retention_enabled,
            dynamic_desired_retention_review_limit: input.dynamic_desired_retention_review_limit,
            dynamic_desired_retention_max_cost_perday_minutes: input
                .dynamic_desired_retention_max_cost_perday_minutes,
        })
    }

    fn compute_fsrs_params_batch(
        &mut self,
        input: scheduler::ComputeFsrsParamsBatchRequest,
    ) -> Result<scheduler::ComputeFsrsParamsBatchResponse> {
        let mut response_meta = Vec::with_capacity(input.items.len());
        let mut jobs = Vec::with_capacity(input.items.len());

        for (index, item) in input.items.into_iter().enumerate() {
            let prepared = self.prepare_compute_params(PrepareComputeParamsInput {
                search: &item.search,
                ignore_revlogs_before: item.ignore_revlogs_before_ms.into(),
                current_params: &item.current_params,
                num_of_relearning_steps: item.num_of_relearning_steps as usize,
                include_same_day_reviews: item.include_same_day_reviews,
                enable_scheduling_penalties: item.enable_scheduling_penalties.unwrap_or(false),
                model_version_override: item.fsrs_version.map(health_check_model_version),
                dynamic_desired_retention_enabled: item.dynamic_desired_retention_enabled,
                historical_retention: 0.9,
                desired_retention: 0.9,
                dynamic_desired_retention_simulator_options:
                    DynamicDesiredRetentionSimulatorOptions {
                        review_limit: item.dynamic_desired_retention_review_limit,
                        max_cost_perday_minutes: item
                            .dynamic_desired_retention_max_cost_perday_minutes,
                    },
            })?;
            response_meta.push((item.id.clone(), item.name.clone()));
            jobs.push(ComputeParamsBatchInput {
                index,
                name: item.name,
                prepared,
            });
        }

        let items = self
            .compute_params_batch(jobs)?
            .into_iter()
            .map(|output| {
                let (id, name) = &response_meta[output.index];
                let params = output.result?;
                Ok(scheduler::compute_fsrs_params_batch_response::Item {
                    id: id.clone(),
                    name: name.clone(),
                    params: params.params,
                    fsrs_items: params.fsrs_items,
                    fsrs_dynamic_desired_retention_params: params
                        .fsrs_dynamic_desired_retention_params,
                    fsrs_dynamic_desired_retention_weights: params
                        .fsrs_dynamic_desired_retention_weights,
                    fsrs_dynamic_desired_retention_avg_drs: params
                        .fsrs_dynamic_desired_retention_avg_drs,
                    fsrs_dynamic_desired_retention_fsrs_eq_weights: params
                        .fsrs_dynamic_desired_retention_fsrs_eq_weights,
                    fsrs_dynamic_desired_retention_fsrs_eq_drs: params
                        .fsrs_dynamic_desired_retention_fsrs_eq_drs,
                    fsrs_dynamic_desired_retention_fixed_target_weights: params
                        .fsrs_dynamic_desired_retention_fixed_target_weights,
                    fsrs_dynamic_desired_retention_fixed_target_drs: params
                        .fsrs_dynamic_desired_retention_fixed_target_drs,
                    fsrs_dynamic_desired_retention_min: params.fsrs_dynamic_desired_retention_min,
                    fsrs_dynamic_desired_retention_max: params.fsrs_dynamic_desired_retention_max,
                })
            })
            .collect::<Result<Vec<_>>>()?;

        Ok(scheduler::ComputeFsrsParamsBatchResponse { items })
    }

    fn simulate_fsrs_review(
        &mut self,
        input: SimulateFsrsReviewRequest,
    ) -> Result<SimulateFsrsReviewResponse> {
        self.simulate_review(input)
    }

    fn simulate_fsrs_workload(
        &mut self,
        input: SimulateFsrsReviewRequest,
    ) -> Result<SimulateFsrsWorkloadResponse> {
        self.simulate_workload(input)
    }

    fn compute_optimal_retention(
        &mut self,
        input: SimulateFsrsReviewRequest,
    ) -> Result<ComputeOptimalRetentionResponse> {
        Ok(ComputeOptimalRetentionResponse {
            optimal_retention: self.compute_optimal_retention(input)?,
        })
    }

    fn get_fsrs_new_card_intervals(
        &mut self,
        input: scheduler::GetFsrsNewCardIntervalsRequest,
    ) -> Result<generic::StringList> {
        let requested_short_term_with_steps = input.fsrs_short_term_with_steps_enabled;
        let requested_learning_queues_disabled = input.fsrs_learning_queues_disabled;
        let config = crate::deckconfig::DeckConfig {
            inner: input.config.unwrap_or_default(),
            ..Default::default()
        };
        let fsrs = FSRS::new(config.fsrs_params())?;
        let params = config.fsrs_params();
        let fsrs_allow_short_term = if params.len() >= 19 {
            params[17] > 0.0 && params[18] > 0.0
        } else {
            false
        };
        let fsrs_short_term_with_steps_enabled = selected_short_term_with_steps_for_preview(
            requested_short_term_with_steps,
            self.get_config_bool(BoolKey::FsrsShortTermWithStepsEnabled),
        );
        let fsrs_learning_queues_disabled = requested_learning_queues_disabled
            .unwrap_or_else(|| self.get_config_bool(BoolKey::FsrsLearningQueuesDisabled));
        let review_fuzz_config = self.review_fuzz_config();
        let make_ctx = |memory_state: Option<fsrs::MemoryState>,
                        days_elapsed: f32|
         -> Result<crate::scheduler::states::StateContext<'_>> {
            let fsrs_next_states = fsrs.next_states_with_elapsed_days(
                memory_state,
                config.inner.desired_retention,
                days_elapsed,
            )?;
            let fsrs_again_s90 = if config.inner.leech_only_if_young {
                Some(fsrs_memory_state_for_params(params, fsrs_next_states.again.memory)?.stability)
            } else {
                None
            };
            Ok(crate::scheduler::states::StateContext {
                fuzz_factor: None,
                fsrs_next_states: Some(fsrs_next_states),
                fsrs_short_term_with_steps_enabled,
                fsrs_learning_queues_disabled,
                fsrs_allow_short_term,
                fsrs_fractional_intervals: uses_fractional_intervals(params),
                steps: crate::scheduler::states::steps::LearningSteps::new(
                    &config.inner.learn_steps,
                ),
                graduating_interval_good: config.inner.graduating_interval_good,
                graduating_interval_easy: config.inner.graduating_interval_easy,
                initial_ease_factor: config.inner.initial_ease,
                hard_multiplier: config.inner.hard_multiplier,
                easy_multiplier: config.inner.easy_multiplier,
                interval_multiplier: config.inner.interval_multiplier,
                review_fuzz_config,
                maximum_review_interval: config.inner.maximum_review_interval,
                fsrs_minimum_interval_secs: config.inner.fsrs_minimum_interval_secs,
                leech_threshold: config.inner.leech_threshold,
                count_review_lapse: true,
                leech_only_if_young: config.inner.leech_only_if_young,
                fsrs_again_s90,
                load_balancer_ctx: None,
                relearn_steps: crate::scheduler::states::steps::LearningSteps::new(
                    &config.inner.relearn_steps,
                ),
                lapse_multiplier: config.inner.lapse_multiplier,
                minimum_lapse_interval: config.inner.minimum_lapse_interval,
                in_filtered_deck: false,
                preview_delays: PreviewDelays::default(),
            })
        };
        let next_followup_states =
            |state: &crate::scheduler::states::CardState| -> Result<SchedulingStates> {
                let (memory_state, days_elapsed) = match state {
                    crate::scheduler::states::CardState::Normal(
                        crate::scheduler::states::NormalState::Learning(state),
                    ) => (
                        state.memory_state.map(Into::into),
                        if state.scheduled_secs == 0 {
                            0.0
                        } else {
                            state.scheduled_secs as f32 / 86_400.0
                        },
                    ),
                    crate::scheduler::states::CardState::Normal(
                        crate::scheduler::states::NormalState::Review(state),
                    ) => (
                        state.memory_state.map(Into::into),
                        state.scheduled_days as f32,
                    ),
                    crate::scheduler::states::CardState::Normal(
                        crate::scheduler::states::NormalState::Relearning(state),
                    ) => (
                        state.learning.memory_state.map(Into::into),
                        if state.learning.scheduled_secs == 0 {
                            state.review.scheduled_days as f32
                        } else {
                            state.learning.scheduled_secs as f32 / 86_400.0
                        },
                    ),
                    crate::scheduler::states::CardState::Normal(
                        crate::scheduler::states::NormalState::New(_),
                    )
                    | crate::scheduler::states::CardState::Filtered(_) => (None, 0.0),
                };
                let ctx = make_ctx(memory_state, days_elapsed)?;
                Ok(state.next_states(&ctx))
            };
        let ctx = make_ctx(None, 0.0)?;
        let states = LearnState {
            remaining_steps: u32::from(!config.inner.learn_steps.is_empty()),
            scheduled_secs: 0,
            elapsed_secs: 0,
            memory_state: None,
        }
        .next_states(&ctx);
        let base = self.describe_next_states(&states)?;
        let again_followup_labels =
            self.describe_next_states(&next_followup_states(&states.again)?)?;
        let good_followup_labels =
            self.describe_next_states(&next_followup_states(&states.good)?)?;
        Ok(generic::StringList {
            vals: vec![
                base[0].clone(),
                base[1].clone(),
                base[2].clone(),
                base[3].clone(),
                again_followup_labels[2].clone(),
                again_followup_labels[0].clone(),
                good_followup_labels[0].clone(),
                good_followup_labels[2].clone(),
            ],
        })
    }

    fn evaluate_params(
        &mut self,
        input: scheduler::EvaluateParamsRequest,
    ) -> Result<scheduler::EvaluateParamsResponse> {
        let model_version = health_check_model_version(input.fsrs_version);
        let ret = self.evaluate_params(
            &input.search,
            input.search_for_training.as_deref(),
            input.ignore_revlogs_before_ms.into(),
            input.num_of_relearning_steps as usize,
            model_version,
            input.include_same_day_reviews,
            input.include_same_day_reviews_for_training,
            input.enable_scheduling_penalties.unwrap_or(false),
        )?;
        Ok(scheduler::EvaluateParamsResponse {
            log_loss: ret.log_loss,
            rmse_bins: ret.rmse_bins,
        })
    }

    fn evaluate_params_legacy(
        &mut self,
        input: scheduler::EvaluateParamsLegacyRequest,
    ) -> Result<scheduler::EvaluateParamsResponse> {
        let ret = self.evaluate_params_legacy(
            &input.params,
            &input.search,
            input.ignore_revlogs_before_ms.into(),
            input.include_same_day_reviews,
        )?;
        Ok(scheduler::EvaluateParamsResponse {
            log_loss: ret.log_loss,
            rmse_bins: ret.rmse_bins,
        })
    }

    fn get_optimal_retention_parameters(
        &mut self,
        input: scheduler::GetOptimalRetentionParametersRequest,
    ) -> Result<scheduler::GetOptimalRetentionParametersResponse> {
        let revlogs = self
            .search_cards_into_table(&input.search, SortMode::NoOrder)?
            .col
            .storage
            .get_revlog_entries_for_searched_cards_in_card_order()?;
        let simulator_config = self.get_optimal_retention_parameters(revlogs)?;
        Ok(GetOptimalRetentionParametersResponse {
            deck_size: simulator_config.deck_size as u32,
            learn_span: simulator_config.learn_span as u32,
            max_cost_perday: simulator_config.max_cost_perday,
            max_ivl: simulator_config.max_ivl,
            first_rating_prob: simulator_config.first_rating_prob.to_vec(),
            review_rating_prob: simulator_config.review_rating_prob.to_vec(),
            loss_aversion: 1.0,
            learn_limit: simulator_config.learn_limit as u32,
            review_limit: simulator_config.review_limit as u32,
            learning_step_transitions: simulator_config
                .learning_step_transitions
                .iter()
                .flatten()
                .cloned()
                .collect(),
            relearning_step_transitions: simulator_config
                .relearning_step_transitions
                .iter()
                .flatten()
                .cloned()
                .collect(),
            state_rating_costs: simulator_config
                .state_rating_costs
                .iter()
                .flatten()
                .cloned()
                .collect(),
            learning_step_count: simulator_config.learning_step_count as u32,
            relearning_step_count: simulator_config.relearning_step_count as u32,
        })
    }

    fn compute_memory_state(&mut self, input: cards::CardId) -> Result<ComputeMemoryStateResponse> {
        self.compute_memory_state(input.into())
    }

    fn fsrs_six_current_scores(
        &mut self,
        input: cards::CardIds,
    ) -> Result<scheduler::Fsrs6CurrentScoresResponse> {
        let (scores, missing_parameters) = self.fsrs6_comparison_current_scores(
            &input.cids.into_iter().map(Into::into).collect::<Vec<_>>(),
        )?;
        Ok(scheduler::Fsrs6CurrentScoresResponse {
            scores: scores
                .into_iter()
                .map(
                    |(card_id, retrievability)| scheduler::fsrs6_current_scores_response::Score {
                        card_id: card_id.0,
                        retrievability,
                    },
                )
                .collect(),
            missing_parameters: missing_parameters as u32,
        })
    }

    fn fsrs_six_comparison_fingerprint(
        &mut self,
        input: generic::String,
    ) -> Result<generic::String> {
        let revlogs = self.revlog_for_srs(&input.val)?;
        let next_day_at = self.timing_today()?.next_day_at;
        Ok(generic::String {
            val: fsrs_six_history_fingerprint(&revlogs, next_day_at),
        })
    }

    fn get_fsrs_preset_for_card(
        &mut self,
        input: cards::CardId,
    ) -> Result<FsrsPresetForCardResponse> {
        let card_id = input.into();
        let card = self.storage.get_card(card_id)?.or_not_found(card_id)?;
        let preset = self.fsrs_preset_for_card(&card)?;
        Ok(fsrs_preset_to_proto(preset))
    }

    fn fuzz_delta(&mut self, input: FuzzDeltaRequest) -> Result<FuzzDeltaResponse> {
        Ok(FuzzDeltaResponse {
            delta_days: self.get_fuzz_delta(input.card_id.into(), input.interval)?,
        })
    }

    fn scheduling_states_with_intervals(
        &mut self,
        input: SchedulingStatesWithIntervalsRequest,
    ) -> Result<anki_proto::scheduler::SchedulingStates> {
        self.scheduling_states_with_intervals(
            CardId(input.card_id),
            [input.again, input.hard, input.good, input.easy],
        )
        .map(Into::into)
    }

    fn fsrs_current_retrievability(
        &mut self,
        input: FsrsCurrentRetrievabilityRequest,
    ) -> Result<FsrsCurrentRetrievabilityResponse> {
        Ok(FsrsCurrentRetrievabilityResponse {
            retrievability: self.fsrs_current_retrievability_for_card(
                input.card_id.into(),
                input.stability,
                input.elapsed_days,
            )?,
        })
    }

    fn fsrs_next_interval(
        &mut self,
        input: FsrsNextIntervalRequest,
    ) -> Result<FsrsNextIntervalResponse> {
        Ok(FsrsNextIntervalResponse {
            interval: self.fsrs_next_interval_for_card(
                input.card_id.into(),
                input.stability,
                input.desired_retention,
            )?,
        })
    }

    fn fsrs_interval_at_retrievability(
        &mut self,
        input: scheduler::FsrsIntervalAtRetrievabilityRequest,
    ) -> Result<FsrsIntervalAtRetrievabilityResponse> {
        Ok(FsrsIntervalAtRetrievabilityResponse {
            interval: self.fsrs_interval_at_retrievability_for_card(
                input.card_id.into(),
                input.stability,
                input.target_retrievability,
            )?,
        })
    }

    fn fsrs_interval_at_retrievability_batch(
        &mut self,
        input: FsrsIntervalAtRetrievabilityBatchRequest,
    ) -> Result<FsrsIntervalAtRetrievabilityBatchResponse> {
        let cards: Vec<(CardId, f32)> = input
            .items
            .iter()
            .map(|item| (item.card_id.into(), item.stability))
            .collect();
        let intervals =
            self.fsrs_interval_at_retrievability_for_cards(&cards, input.target_retrievability)?;
        let items = input
            .items
            .into_iter()
            .zip(intervals)
            .map(|(item, interval)| {
                scheduler::fsrs_interval_at_retrievability_batch_response::Item {
                    card_id: item.card_id,
                    interval,
                }
            })
            .collect();
        Ok(FsrsIntervalAtRetrievabilityBatchResponse { items })
    }

    fn fsrs_interval_at_retrievability_variable_batch(
        &mut self,
        input: FsrsIntervalAtRetrievabilityVariableBatchRequest,
    ) -> Result<FsrsIntervalAtRetrievabilityVariableBatchResponse> {
        let cards: Vec<(CardId, f32, f32)> = input
            .items
            .iter()
            .map(|item| {
                (
                    item.card_id.into(),
                    item.stability,
                    item.target_retrievability,
                )
            })
            .collect();
        let intervals = self.fsrs_interval_at_retrievability_for_card_targets(&cards)?;
        let items = input
            .items
            .into_iter()
            .zip(intervals)
            .map(|(item, interval)| {
                scheduler::fsrs_interval_at_retrievability_variable_batch_response::Item {
                    request_index: item.request_index,
                    interval,
                }
            })
            .collect();
        Ok(FsrsIntervalAtRetrievabilityVariableBatchResponse { items })
    }

    fn fsrs_desired_retention_for_intervals_batch(
        &mut self,
        input: FsrsDesiredRetentionForIntervalsBatchRequest,
    ) -> Result<FsrsDesiredRetentionForIntervalsBatchResponse> {
        let cards: Vec<(CardId, f32)> = input
            .items
            .iter()
            .map(|item| (item.card_id.into(), item.desired_retention))
            .collect();
        let targets = self.fsrs_desired_retention_for_intervals(&cards)?;
        let items = input
            .items
            .into_iter()
            .zip(targets)
            .map(|(item, target)| {
                scheduler::fsrs_desired_retention_for_intervals_batch_response::Item {
                    request_index: item.request_index,
                    interval_target_desired_retention: target.interval_target_desired_retention,
                    dynamic_desired_retentions: target
                        .dynamic_desired_retentions
                        .map(|retentions| retentions.to_vec())
                        .unwrap_or_default(),
                    dynamic_desired_retention_enabled: target.dynamic_desired_retention_enabled,
                }
            })
            .collect();
        Ok(FsrsDesiredRetentionForIntervalsBatchResponse { items })
    }

    fn fsrs_interval_at_retrievability_by_config_batch(
        &mut self,
        input: FsrsIntervalAtRetrievabilityByConfigBatchRequest,
    ) -> Result<FsrsIntervalAtRetrievabilityByConfigBatchResponse> {
        let configs: Vec<(DeckConfigId, f32)> = input
            .items
            .iter()
            .map(|item| (DeckConfigId(item.config_id), item.stability))
            .collect();
        let intervals = self
            .fsrs_interval_at_retrievability_for_configs(&configs, input.target_retrievability)?;
        let items = input
            .items
            .into_iter()
            .zip(intervals)
            .map(|(item, interval)| {
                scheduler::fsrs_interval_at_retrievability_by_config_batch_response::Item {
                    request_index: item.request_index,
                    interval,
                }
            })
            .collect();
        Ok(FsrsIntervalAtRetrievabilityByConfigBatchResponse { items })
    }

    fn set_rwkv_review_queue_scores(&mut self, input: RwkvReviewQueueScoresRequest) -> Result<()> {
        let scores = rwkv_score_entries(input.scores)?;
        self.set_rwkv_review_queue_score_entries(input.deck_id.into(), scores)
    }

    fn patch_answered_card_rwkv_review_queue_score(
        &mut self,
        input: RwkvAnsweredCardQueueScorePatchRequest,
    ) -> Result<()> {
        let entry = input.score.map(rwkv_score_entry).transpose()?;
        self.patch_answered_card_rwkv_review_queue_score_entry(
            input.deck_id.into(),
            input.card_id.into(),
            entry,
        )
    }

    fn set_rwkv_deck_count_scores(&mut self, input: RwkvReviewQueueScoresRequest) -> Result<()> {
        let scores = rwkv_score_entries(input.scores)?;
        self.set_rwkv_deck_count_score_entries(input.deck_id.into(), scores)
    }

    fn clear_rwkv_deck_count_scores(&mut self) -> Result<()> {
        Collection::clear_rwkv_deck_count_scores(self);
        Ok(())
    }

    fn update_rwkv_review_queue_intervening_reviews(
        &mut self,
        input: RwkvReviewQueueInterveningReviewsRequest,
    ) -> Result<()> {
        let intervening_reviews_by_card_id = input
            .items
            .into_iter()
            .map(|item| (item.card_id.into(), item.intervening_reviews))
            .collect();
        Collection::update_rwkv_review_queue_intervening_reviews(
            self,
            input.deck_id.into(),
            intervening_reviews_by_card_id,
        )
    }

    fn set_rwkv_stats_graph_scores(&mut self, input: RwkvStatsGraphScoresRequest) -> Result<()> {
        let mut scores = HashMap::with_capacity(input.scores.len());
        for score in input.scores {
            require!(
                score.retrievability.is_finite() && (0.0..=1.0).contains(&score.retrievability),
                "invalid RWKV retrievability"
            );
            if let Some(target_retention) = score.target_retention {
                require!(
                    target_retention.is_finite() && (0.0..=1.0).contains(&target_retention),
                    "invalid RWKV target retention"
                );
            }
            if let Some(curve_retrievability) = score.curve_retrievability {
                require!(
                    curve_retrievability.is_finite() && (0.0..=1.0).contains(&curve_retrievability),
                    "invalid RWKV-Curve retrievability"
                );
            }
            scores.insert(
                score.card_id.into(),
                RwkvStatsGraphScoreEntry {
                    retrievability: score.retrievability,
                    curve_retrievability: score.curve_retrievability,
                    intervening_reviews: score.intervening_reviews,
                    target_retention: score.target_retention,
                    curve_due: score.curve_due.unwrap_or(false),
                },
            );
        }
        self.set_rwkv_stats_graph_score_entries(input.search, scores)
    }

    fn set_rwkv_card_info_score(&mut self, input: RwkvCardInfoScoreRequest) -> Result<()> {
        if let Some(retrievability) = input.retrievability {
            require!(
                retrievability.is_finite() && (0.0..=1.0).contains(&retrievability),
                "invalid RWKV retrievability"
            );
        }
        if let Some(curve_retrievability) = input.curve_retrievability {
            require!(
                curve_retrievability.is_finite() && (0.0..=1.0).contains(&curve_retrievability),
                "invalid RWKV-Curve retrievability"
            );
        }
        self.set_rwkv_card_info_scores(
            input.card_id.into(),
            input.retrievability,
            input.curve_retrievability,
        )
    }

    fn get_rwkv_retrievability_score(
        &mut self,
        input: cards::CardId,
    ) -> Result<RwkvRetrievabilityScoreResponse> {
        let days_elapsed = self.timing_today()?.days_elapsed;
        Ok(RwkvRetrievabilityScoreResponse {
            retrievability: self.rwkv_retrievability_score_for_day(input.into(), days_elapsed),
        })
    }

    fn set_rwkv_review_retrievability_cache_rows(
        &mut self,
        input: RwkvReviewRetrievabilityCacheRowsRequest,
    ) -> Result<generic::UInt32> {
        require!(
            !input.source.is_empty(),
            "missing RWKV retrievability source"
        );
        let mut rows = Vec::with_capacity(input.rows.len());
        for row in input.rows {
            require!(row.revlog_id > 0, "invalid RWKV review id");
            require!(
                row.prediction.is_finite() && (0.0..=1.0).contains(&row.prediction),
                "invalid RWKV retrievability"
            );
            let Some(sample_role) = RwkvReviewRetrievabilitySampleRole::from_str(&row.sample_role)
            else {
                invalid_input!("invalid RWKV retrievability sample role");
            };
            rows.push(RwkvReviewRetrievabilityCacheRow {
                revlog_id: RevlogId(row.revlog_id),
                prediction: row.prediction,
                sample_role,
                fold_index: row.fold_index,
            });
        }
        let count = self
            .storage
            .set_rwkv_review_retrievability_predictions(&rows, &input.source)?;
        Ok(generic::UInt32 { val: count as u32 })
    }

    fn clear_review_retrievability_cache(&mut self) -> Result<()> {
        self.storage.clear_review_retrievability_cache()
    }

    fn prune_rwkv_review_retrievability_cache_rows(
        &mut self,
        input: PruneRwkvReviewRetrievabilityCacheRowsRequest,
    ) -> Result<generic::UInt32> {
        require!(
            !input.source.is_empty()
                && input
                    .superseded_sources
                    .iter()
                    .all(|source| !source.is_empty() && *source != input.source),
            "invalid RWKV retrievability source"
        );
        let mut keep = Vec::with_capacity(input.keep.len());
        for key in input.keep {
            require!(key.revlog_id > 0, "invalid RWKV review id");
            let Some(sample_role) = RwkvReviewRetrievabilitySampleRole::from_str(&key.sample_role)
            else {
                invalid_input!("invalid RWKV retrievability sample role");
            };
            keep.push((RevlogId(key.revlog_id), sample_role, key.fold_index));
        }
        let deleted = self.storage.prune_rwkv_review_retrievability_rows(
            &input.source,
            &keep,
            &input.superseded_sources,
        )?;
        Ok(generic::UInt32 {
            val: deleted as u32,
        })
    }

    fn apply_rwkv_review_reschedule(
        &mut self,
        input: RwkvReviewRescheduleRequest,
    ) -> Result<anki_proto::collection::OpChangesWithCount> {
        let items = input
            .items
            .into_iter()
            .map(|item| RwkvReviewRescheduleItem {
                card_id: item.card_id.into(),
                interval_days: item.interval_days,
                elapsed_days: item.elapsed_days,
                s90: item.s90,
                target_retention: item.target_retention,
            })
            .collect();
        self.apply_rwkv_review_reschedule(items).map(Into::into)
    }

    fn rwkv_review_input_rows_for_cards(
        &mut self,
        input: RwkvReviewInputRowsForCardsRequest,
    ) -> Result<RwkvReviewInputRowsForCardsResponse> {
        Collection::rwkv_review_input_rows_for_cards(self, input)
    }

    fn rwkv_review_input_rows_for_search(
        &mut self,
        input: RwkvReviewInputRowsForSearchRequest,
    ) -> Result<RwkvReviewInputRowsForCardsResponse> {
        Collection::rwkv_review_input_rows_for_search(self, input)
    }

    fn rwkv_review_input_rows_for_deck_review_queue(
        &mut self,
        input: RwkvReviewInputRowsForDeckReviewQueueRequest,
    ) -> Result<RwkvReviewInputRowsForCardsResponse> {
        Collection::rwkv_review_input_rows_for_deck_review_queue(self, input)
    }

    fn get_fsrs_preset_ids_for_cards(
        &mut self,
        input: cards::CardIds,
    ) -> Result<FsrsPresetIdsForCardsResponse> {
        let mut cards = Vec::with_capacity(input.cids.len());
        for cid in input.cids {
            let card_id = CardId(cid);
            cards.push(self.storage.get_card(card_id)?.or_not_found(card_id)?);
        }

        let presets = self.fsrs_presets_for_cards(&cards)?;
        let items = cards
            .into_iter()
            .filter_map(|card| {
                presets.get(&card.id).map(|preset| {
                    scheduler::fsrs_preset_ids_for_cards_response::Item {
                        card_id: card.id.0,
                        preset_id: fsrs_preset_id_to_string(preset.id.clone()),
                    }
                })
            })
            .collect();

        Ok(FsrsPresetIdsForCardsResponse { items })
    }

    fn rwkv_historical_review_fingerprint(
        &mut self,
        input: RwkvHistoricalReviewFingerprintRequest,
    ) -> Result<RwkvHistoricalReviewFingerprintResponse> {
        Collection::rwkv_historical_review_fingerprint(self, input)
    }

    fn rwkv_historical_review_inputs(
        &mut self,
        input: anki_proto::scheduler::RwkvHistoricalReviewInputsRequest,
    ) -> Result<anki_proto::scheduler::RwkvHistoricalReviewInputsResponse> {
        Collection::rwkv_historical_review_inputs(self, input)
    }

    fn rwkv_prepare_offline(
        &mut self,
        input: anki_proto::scheduler::RwkvPrepareOfflineRequest,
    ) -> Result<anki_proto::scheduler::RwkvPrepareOfflineResponse> {
        Collection::rwkv_prepare_offline(self, input)
    }

    fn rwkv_offline_instant_pass_step(
        &mut self,
        input: anki_proto::scheduler::RwkvOfflineInstantPassStepRequest,
    ) -> Result<anki_proto::scheduler::RwkvOfflineInstantPassProgress> {
        Collection::rwkv_offline_instant_pass_step(self, input)
    }

    fn rwkv_offline_forecast(
        &mut self,
        input: anki_proto::scheduler::RwkvOfflineForecastRequest,
    ) -> Result<anki_proto::scheduler::RwkvOfflineForecastResponse> {
        Collection::rwkv_offline_forecast(self, input)
    }

    fn rwkv_offline_calibration(
        &mut self,
        input: anki_proto::scheduler::RwkvOfflineCalibrationRequest,
    ) -> Result<anki_proto::scheduler::RwkvOfflineCalibrationResponse> {
        Collection::rwkv_offline_calibration(
            self,
            input.review_ids,
            input.all_reviews,
            input.check_only,
        )
    }

    fn rwkv_offline_curve_reschedule(
        &mut self,
        input: anki_proto::decks::DeckId,
    ) -> Result<anki_proto::collection::OpChangesWithCount> {
        Collection::rwkv_offline_curve_reschedule(self, input.did.into()).map(Into::into)
    }

    fn rwkv_offline_workload_start(
        &mut self,
        input: anki_proto::scheduler::SimulateFsrsReviewRequest,
    ) -> Result<()> {
        Collection::rwkv_offline_workload_start(self, input)
    }

    fn rwkv_offline_workload_result(
        &mut self,
    ) -> Result<anki_proto::scheduler::RwkvOfflineWorkloadResultResponse> {
        Collection::rwkv_offline_workload_result(self)
    }

    fn rwkv_offline_workload_cancel(&mut self) -> Result<()> {
        Collection::rwkv_offline_workload_cancel(self);
        Ok(())
    }

    fn rwkv_offline_workload_progress(
        &mut self,
    ) -> Result<anki_proto::collection::ComputeRetentionProgress> {
        Ok(Collection::rwkv_offline_workload_progress(self))
    }

    fn rwkv_offline_workload_available(&mut self) -> Result<generic::Bool> {
        Ok(Collection::rwkv_offline_workload_available(self).into())
    }

    fn rwkv_answer_time_history_features(
        &mut self,
        input: anki_proto::scheduler::RwkvAnswerTimeHistoryFeaturesRequest,
    ) -> Result<anki_proto::scheduler::RwkvAnswerTimeHistoryFeaturesResponse> {
        Collection::rwkv_answer_time_history_features(self, input)
    }
}

fn selected_short_term_with_steps_for_preview(requested: Option<bool>, stored: bool) -> bool {
    requested.unwrap_or(stored)
}

fn fsrs_preset_to_proto(preset: FsrsPreset) -> FsrsPresetForCardResponse {
    let (
        fsrs_dynamic_desired_retention_enabled,
        fsrs_dynamic_desired_retention_params,
        fsrs_dynamic_desired_retention_weights,
        fsrs_dynamic_desired_retention_avg_drs,
        fsrs_dynamic_desired_retention_min,
        fsrs_dynamic_desired_retention_max,
        fsrs_dynamic_desired_retention_fsrs_eq_weights,
        fsrs_dynamic_desired_retention_fsrs_eq_drs,
        fsrs_dynamic_desired_retention_fixed_target_weights,
        fsrs_dynamic_desired_retention_fixed_target_drs,
        fsrs_dynamic_desired_retention_clamp,
    ) = if let Some(dynamic_dr) = preset.dynamic_desired_retention {
        let (weights, avg_drs): (Vec<_>, Vec<_>) = dynamic_dr.calibration().iter().copied().unzip();
        let (fsrs_eq_weights, fsrs_eq_drs): (Vec<_>, Vec<_>) = dynamic_dr
            .fsrs_equivalent_calibration()
            .iter()
            .copied()
            .unzip();
        let (fixed_target_weights, fixed_target_drs): (Vec<_>, Vec<_>) = dynamic_dr
            .fixed_target_calibration()
            .iter()
            .copied()
            .unzip();
        (
            true,
            dynamic_dr.policy_params().to_vec(),
            weights,
            avg_drs,
            dynamic_dr.retention_min(),
            dynamic_dr.retention_max(),
            fsrs_eq_weights,
            fsrs_eq_drs,
            fixed_target_weights,
            fixed_target_drs,
            dynamic_dr.clamp_target(),
        )
    } else {
        (
            false,
            vec![],
            vec![],
            vec![],
            0.0,
            0.0,
            vec![],
            vec![],
            vec![],
            vec![],
            false,
        )
    };

    FsrsPresetForCardResponse {
        id: fsrs_preset_id_to_string(preset.id),
        name: preset.name,
        fsrs_version: preset.fsrs_version as i32,
        params: preset.params,
        desired_retention: preset.desired_retention,
        historical_retention: preset.historical_retention,
        ignore_revlogs_before_date: preset.ignore_revlogs_before_date,
        fsrs_dynamic_desired_retention_enabled,
        fsrs_dynamic_desired_retention_params,
        fsrs_dynamic_desired_retention_weights,
        fsrs_dynamic_desired_retention_avg_drs,
        fsrs_dynamic_desired_retention_min,
        fsrs_dynamic_desired_retention_max,
        fsrs_dynamic_desired_retention_fsrs_eq_weights,
        fsrs_dynamic_desired_retention_fsrs_eq_drs,
        fsrs_dynamic_desired_retention_fixed_target_weights,
        fsrs_dynamic_desired_retention_fixed_target_drs,
        fsrs_dynamic_desired_retention_clamp,
    }
}

fn fsrs_preset_id_to_string(id: FsrsPresetId) -> String {
    match id {
        FsrsPresetId::DeckConfig(id) => id.0.to_string(),
        FsrsPresetId::Addon(id) => id,
    }
}

fn health_check_model_version(fsrs_version: i32) -> ComputeParametersVersion {
    match FsrsVersion::try_from(fsrs_version).unwrap_or(FsrsVersion::Seven) {
        FsrsVersion::Seven => ComputeParametersVersion::Fsrs7,
        FsrsVersion::Six | FsrsVersion::Five | FsrsVersion::Four => ComputeParametersVersion::Fsrs6,
    }
}

fn fsrs_calibration_search_for_config(
    decks: &[Deck],
    config_id: DeckConfigId,
) -> Option<SearchNode> {
    let deck_ids = decks
        .iter()
        .filter(|deck| deck.config_id() == Some(config_id))
        .map(|deck| deck.id.to_string())
        .collect::<Vec<_>>();
    (!deck_ids.is_empty()).then(|| SearchNode::DeckIdsWithoutChildren(deck_ids.join(",")))
}

impl Backend {
    /// kuma3: the fork's cache rebuild, also without the validation folds
    /// (only each preset's current parameters over the whole history: one
    /// pass instead of training FSRS once per fold).
    fn rebuild_fsrs_review_retrievability_cache_with(
        &self,
        include_validation_folds: bool,
    ) -> Result<generic::UInt32> {
        let (requests, session) = self.with_col(|col| {
            let decks = col.storage.get_all_decks()?;
            let mut requests = Vec::new();
            for config in col.storage.all_deck_config()? {
                let Some(search) = fsrs_calibration_search_for_config(&decks, config.id) else {
                    continue;
                };
                requests.push(
                    scheduler::ComputeFsrsReviewRetrievabilityCalibrationRequest {
                        search: search.try_into_search()?.to_string(),
                        params: config.fsrs_params().to_vec(),
                        ignore_revlogs_before_ms: ignore_revlogs_before_ms_from_config(&config)?.0,
                        num_of_relearning_steps: config.inner.relearn_steps.len() as u32,
                        include_same_day_reviews: fsrs7_optimize_include_same_day_reviews(&config),
                        fsrs_version: Some(config.inner.fsrs_version),
                        include_validation_folds,
                        enable_scheduling_penalties: Some(fsrs7_enable_scheduling_penalties(
                            &config,
                        )),
                    },
                );
            }
            Ok((requests, col.session_id.clone()))
        })?;
        let mut count = 0;
        for request in requests {
            self.with_col(|col| {
                require!(
                    Arc::ptr_eq(&col.session_id, &session),
                    "collection changed during cache rebuild"
                );
                Ok(())
            })?;
            count += crate::services::BackendSchedulerService::compute_fsrs_review_retrievability_calibration(
                self, request,
            )?
            .val;
        }
        Ok(generic::UInt32 { val: count })
    }
}

impl crate::services::BackendSchedulerService for Backend {
    fn rebuild_fsrs_review_retrievability_cache(&self) -> Result<generic::UInt32> {
        self.rebuild_fsrs_review_retrievability_cache_with(true)
    }

    fn fsrs_calibration_final_fit(&self) -> Result<generic::UInt32> {
        self.rebuild_fsrs_review_retrievability_cache_with(false)
    }

    fn compute_fsrs_six_comparison(
        &self,
        input: scheduler::ComputeFsrs6ComparisonRequest,
    ) -> Result<scheduler::ComputeFsrs6ComparisonResponse> {
        require!(
            input.params.len() == 21 && input.params.iter().all(|param| param.is_finite()),
            "FSRS-6 comparison requires 21 saved parameters"
        );
        let mut progress = self.new_progress_handler();
        let (revlogs, next_day_at) = self.with_col(|col| {
            progress.check_cancelled()?;
            Ok((
                col.revlog_for_srs(&input.search)?,
                col.timing_today()?.next_day_at,
            ))
        })?;
        let history_fingerprint = fsrs_six_history_fingerprint(&revlogs, next_day_at);
        let context = FsrsReviewPredictionContext::from_revlogs(
            revlogs,
            next_day_at,
            PrepareComputeParamsInput {
                search: &input.search,
                ignore_revlogs_before: input.ignore_revlogs_before_ms.into(),
                current_params: &input.params,
                num_of_relearning_steps: input.num_of_relearning_steps as usize,
                include_same_day_reviews: None,
                enable_scheduling_penalties: false,
                model_version_override: Some(ComputeParametersVersion::Fsrs6),
                dynamic_desired_retention_enabled: false,
                historical_retention: 0.9,
                desired_retention: 0.9,
                dynamic_desired_retention_simulator_options:
                    DynamicDesiredRetentionSimulatorOptions::default(),
            },
            &mut progress,
        )?;
        let (mut rows, mut progress) = compute_fsrs_review_retrievability_calibration_rows(
            &input.params,
            &context,
            input.include_validation_folds,
            progress,
        )?;
        progress.check_cancelled()?;
        rows.sort_unstable_by_key(|row| (row.revlog_id, row.fold_index));
        let mut final_fit = Vec::new();
        let mut validation_fold = Vec::new();
        for row in rows {
            let target = match row.sample_role {
                FsrsReviewRetrievabilitySampleRole::FinalFit => &mut final_fit,
                FsrsReviewRetrievabilitySampleRole::ValidationFold => &mut validation_fold,
                FsrsReviewRetrievabilitySampleRole::PostOptimization => continue,
            };
            target.extend_from_slice(&row.revlog_id.0.to_le_bytes());
            target.extend_from_slice(&row.prediction.to_le_bytes());
        }
        Ok(scheduler::ComputeFsrs6ComparisonResponse {
            final_fit,
            validation_fold,
            history_fingerprint,
        })
    }

    fn compute_fsrs_review_retrievability_calibration(
        &self,
        input: scheduler::ComputeFsrsReviewRetrievabilityCalibrationRequest,
    ) -> Result<generic::UInt32> {
        // Reset stale progress before any work, so cancellation during input
        // preparation survives the transition into training.
        let mut progress = self.new_progress_handler();
        let (revlogs, next_day_at, session) = self.with_col(|col| {
            progress.check_cancelled()?;
            let next_day_at = col.timing_today()?.next_day_at;
            let revlogs = col.revlog_for_srs(&input.search)?;
            Ok((revlogs, next_day_at, col.session_id.clone()))
        })?;
        progress.check_cancelled()?;
        let context = FsrsReviewPredictionContext::from_revlogs(
            revlogs,
            next_day_at,
            PrepareComputeParamsInput {
                search: &input.search,
                ignore_revlogs_before: input.ignore_revlogs_before_ms.into(),
                current_params: &input.params,
                num_of_relearning_steps: input.num_of_relearning_steps as usize,
                include_same_day_reviews: input.include_same_day_reviews,
                enable_scheduling_penalties: input.enable_scheduling_penalties.unwrap_or(false),
                model_version_override: input.fsrs_version.map(health_check_model_version),
                dynamic_desired_retention_enabled: false,
                historical_retention: 0.9,
                desired_retention: 0.9,
                dynamic_desired_retention_simulator_options:
                    DynamicDesiredRetentionSimulatorOptions::default(),
            },
            &mut progress,
        )?;
        let (mut rows, mut progress) = compute_fsrs_review_retrievability_calibration_rows(
            &input.params,
            &context,
            input.include_validation_folds,
            progress,
        )?;
        drop(context);
        progress.check_cancelled()?;
        // Visit cache pages in review-id order, so small transactions do not
        // repeatedly dirty the same pages for different cards' histories.
        rows.sort_unstable_by_key(|row| (row.revlog_id, row.fold_index));
        // Saving derived cache rows in bounded transactions lets collection
        // reads and cancellation run between batches. Interrupted callers must
        // retry the job instead of treating the partial cache as complete.
        let stored =
            save_fsrs_calibration_rows(self, &session, &rows, || progress.check_cancelled())?;
        tracing::debug!(
            predictions = stored,
            "stored FSRS review retrievability calibration cache"
        );
        Ok(generic::UInt32 { val: stored as u32 })
    }

    fn compute_fsrs_params_from_items(
        &self,
        req: scheduler::ComputeFsrsParamsFromItemsRequest,
    ) -> Result<scheduler::ComputeFsrsParamsResponse> {
        let fsrs_items = req.items.len() as u32;
        let params = compute_parameters(ComputeParametersInput {
            training_config: None,
            train_set: req.items.into_iter().map(fsrs_item_proto_to_fsrs).collect(),
            card_ids: None,
            progress: None,
            enable_short_term: true,
            enable_sched_penalties: false,
            model_version: ComputeParametersVersion::default(),
            num_relearning_steps: None,
        })?;
        Ok(ComputeFsrsParamsResponse {
            params,
            fsrs_items,
            health_check_passed: None,
            fsrs_dynamic_desired_retention_params: Vec::new(),
            fsrs_dynamic_desired_retention_weights: Vec::new(),
            fsrs_dynamic_desired_retention_avg_drs: Vec::new(),
            fsrs_dynamic_desired_retention_fsrs_eq_weights: Vec::new(),
            fsrs_dynamic_desired_retention_fsrs_eq_drs: Vec::new(),
            fsrs_dynamic_desired_retention_fixed_target_weights: Vec::new(),
            fsrs_dynamic_desired_retention_fixed_target_drs: Vec::new(),
            fsrs_dynamic_desired_retention_min: 0.0,
            fsrs_dynamic_desired_retention_max: 0.0,
        })
    }

    fn fsrs_benchmark(
        &self,
        req: scheduler::FsrsBenchmarkRequest,
    ) -> Result<scheduler::FsrsBenchmarkResponse> {
        let train_set = req
            .train_set
            .into_iter()
            .map(fsrs_item_proto_to_fsrs)
            .collect();
        let params = benchmark(ComputeParametersInput {
            training_config: None,
            train_set,
            card_ids: None,
            progress: None,
            enable_short_term: true,
            enable_sched_penalties: false,
            model_version: ComputeParametersVersion::default(),
            num_relearning_steps: None,
        });
        Ok(FsrsBenchmarkResponse { params })
    }

    fn export_dataset(&self, req: scheduler::ExportDatasetRequest) -> Result<()> {
        self.with_col(|col| {
            col.export_dataset(
                req.min_entries.try_into().unwrap(),
                req.target_path.as_ref(),
            )
        })
    }
}

fn fsrs_item_proto_to_fsrs(item: anki_proto::scheduler::FsrsItem) -> FSRSItem {
    FSRSItem {
        reviews: item
            .reviews
            .into_iter()
            .map(fsrs_review_proto_to_fsrs)
            .collect(),
    }
}

fn fsrs_six_history_fingerprint(
    revlogs: &[crate::revlog::RevlogEntry],
    next_day_at: TimestampSecs,
) -> String {
    let mut hash = blake3::Hasher::new();
    hash.update(&(next_day_at.0.rem_euclid(86_400)).to_le_bytes());
    for row in revlogs {
        hash.update(&row.id.0.to_le_bytes());
        hash.update(&row.cid.0.to_le_bytes());
        hash.update(&[row.button_chosen, row.review_kind as u8]);
        hash.update(&row.interval.to_le_bytes());
        hash.update(&row.last_interval.to_le_bytes());
        hash.update(&row.ease_factor.to_le_bytes());
        hash.update(&row.taken_millis.to_le_bytes());
    }
    hash.finalize().to_hex().to_string()
}

fn fsrs_review_proto_to_fsrs(review: anki_proto::scheduler::FsrsReview) -> FSRSReview {
    FSRSReview {
        delta_t: review.delta_t as f32,
        rating: review.rating,
    }
}

const FSRS_CALIBRATION_SAVE_BATCH_SIZE: usize = 10_000;

fn save_fsrs_calibration_rows(
    backend: &Backend,
    session: &Arc<()>,
    rows: &[FsrsReviewRetrievabilityCacheRow],
    mut check_cancelled: impl FnMut() -> Result<()>,
) -> Result<usize> {
    check_cancelled()?;
    backend.with_col_in_session(session, |_| Ok(()))?;
    let mut stored = 0;
    for chunk in rows.chunks(FSRS_CALIBRATION_SAVE_BATCH_SIZE) {
        check_cancelled()?;
        stored += backend.with_col_in_session(session, |col| {
            col.storage
                .set_fsrs_review_retrievability_predictions(chunk, "fsrs_calibration_recompute")
        })?;
        std::thread::yield_now();
    }
    check_cancelled()?;
    Ok(stored)
}

#[cfg(test)]
mod tests {
    use fsrs::ComputeParametersVersion;

    use super::fsrs_preset_to_proto;
    use super::health_check_model_version;
    use super::selected_short_term_with_steps_for_preview;
    use super::FsrsVersion;
    use crate::scheduler::fsrs::dynamic_desired_retention::DynamicDesiredRetention;
    use crate::scheduler::fsrs::dynamic_desired_retention::DynamicDesiredRetentionFields;
    use crate::scheduler::fsrs::preset::FsrsPreset;
    use crate::scheduler::fsrs::preset::FsrsPresetId;

    #[test]
    fn fsrs_six_fingerprint_tracks_review_edits_and_rollover() {
        use super::fsrs_six_history_fingerprint;
        use crate::prelude::TimestampSecs;
        use crate::revlog::RevlogEntry;

        let mut review = RevlogEntry {
            button_chosen: 3,
            ..Default::default()
        };
        let original = fsrs_six_history_fingerprint(&[review.clone()], TimestampSecs(86_400));
        review.button_chosen = 1;
        assert_ne!(
            original,
            fsrs_six_history_fingerprint(&[review.clone()], TimestampSecs(86_400))
        );
        review.button_chosen = 3;
        assert_ne!(
            original,
            fsrs_six_history_fingerprint(&[review], TimestampSecs(86_401))
        );
    }

    #[test]
    fn fsrs_calibration_search_isolates_presets_with_duplicate_names() -> crate::prelude::Result<()>
    {
        use super::*;

        let mut col = Collection::new();
        let mut first_config = col.get_deck_config(DeckConfigId(1), false)?.unwrap();
        first_config.name = "Shared name".into();
        col.add_or_update_deck_config(&mut first_config)?;
        let mut second_config = first_config.clone();
        second_config.id = DeckConfigId(0);
        col.add_or_update_deck_config(&mut second_config)?;
        assert_ne!(first_config.id, second_config.id);
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut expected = Vec::new();
        for (name, config) in [("First", &first_config), ("Second", &second_config)] {
            let mut deck = col.get_or_create_normal_deck(name)?;
            deck.normal_mut().unwrap().config_id = config.id.0;
            col.add_or_update_deck(&mut deck)?;
            let mut note = nt.new_note();
            col.add_note(&mut note, deck.id)?;
            let cards = col.search_cards(format!("did:{}", deck.id).as_str(), SortMode::NoOrder)?;
            assert_eq!(cards.len(), 1);
            expected.push((config.id, cards));
        }
        let decks = col.storage.get_all_decks()?;
        for (config_id, cards) in expected {
            let search = fsrs_calibration_search_for_config(&decks, config_id).unwrap();
            assert_eq!(col.search_cards(search, SortMode::NoOrder)?, cards);
        }
        assert!(fsrs_calibration_search_for_config(&decks, DeckConfigId(-1)).is_none());
        Ok(())
    }

    #[test]
    fn calibration_save_stops_between_batches_on_cancel_or_reopen() -> crate::prelude::Result<()> {
        use anki_i18n::I18n;
        use anki_proto::collection::OpenCollectionRequest;

        use super::*;
        use crate::services::BackendCollectionService;
        use crate::storage::FsrsReviewRetrievabilitySampleRole;

        for reopen in [false, true] {
            let dir = tempfile::tempdir()?;
            let backend = Backend::new(I18n::template_only(), false);
            let request = OpenCollectionRequest {
                collection_path: dir.path().join("collection.anki2").to_str().unwrap().into(),
                media_folder_path: dir.path().join("media").to_str().unwrap().into(),
                media_db_path: dir.path().join("media.db").to_str().unwrap().into(),
            };
            backend.open_collection(request.clone())?;
            let session = backend.with_col(|col| Ok(col.session_id.clone()))?;
            let rows = (1..=FSRS_CALIBRATION_SAVE_BATCH_SIZE + 1)
                .map(|id| FsrsReviewRetrievabilityCacheRow {
                    revlog_id: (id as i64).into(),
                    prediction: 0.5,
                    sample_role: FsrsReviewRetrievabilitySampleRole::FinalFit,
                    fold_index: -1,
                })
                .collect::<Vec<_>>();
            let mut progress = backend
                .new_progress_handler::<crate::scheduler::fsrs::params::ComputeParamsProgress>();
            let mut checks = 0;
            let result = save_fsrs_calibration_rows(&backend, &session, &rows, || {
                checks += 1;
                if checks == 3 {
                    if reopen {
                        backend.close_collection(Default::default())?;
                        backend.open_collection(request.clone())?;
                    } else {
                        backend.set_wants_abort()?;
                    }
                }
                progress.check_cancelled()
            });
            assert!(matches!(result, Err(AnkiError::Interrupted)));
            backend.with_col(|col| {
                let count: usize = col.storage.db.query_row(
                    "SELECT count(*) FROM search_stats_fsrs_review_retrievability",
                    [],
                    |row| row.get(0),
                )?;
                assert_eq!(count, FSRS_CALIBRATION_SAVE_BATCH_SIZE);
                Ok(())
            })?;
        }
        Ok(())
    }

    #[test]
    fn new_card_interval_preview_prefers_explicit_toggle_when_provided() {
        assert!(selected_short_term_with_steps_for_preview(
            Some(true),
            false
        ));
        assert!(!selected_short_term_with_steps_for_preview(
            Some(false),
            true
        ));
    }

    #[test]
    fn new_card_interval_preview_falls_back_to_stored_toggle() {
        assert!(selected_short_term_with_steps_for_preview(None, true));
        assert!(!selected_short_term_with_steps_for_preview(None, false));
    }

    #[test]
    fn health_check_uses_selected_fsrs6_or_fsrs7_family() {
        assert_eq!(
            health_check_model_version(FsrsVersion::Seven as i32),
            ComputeParametersVersion::Fsrs7
        );
        assert_eq!(
            health_check_model_version(FsrsVersion::Six as i32),
            ComputeParametersVersion::Fsrs6
        );
    }

    #[test]
    fn health_check_maps_fsrs4_and_fsrs5_to_fsrs6_family() {
        assert_eq!(
            health_check_model_version(FsrsVersion::Five as i32),
            ComputeParametersVersion::Fsrs6
        );
        assert_eq!(
            health_check_model_version(FsrsVersion::Four as i32),
            ComputeParametersVersion::Fsrs6
        );
    }

    #[test]
    fn fsrs_preset_response_exposes_dynamic_dr_fields() -> crate::prelude::Result<()> {
        let dynamic_dr = DynamicDesiredRetention::from_fields(DynamicDesiredRetentionFields {
            policy_params: vec![1.0; 15],
            calibration_weights: vec![0.0, 15.0],
            calibration_avg_drs: vec![0.9, 0.8],
            fsrs_equivalent_weights: vec![0.0, 15.0],
            fsrs_equivalent_drs: vec![0.91, 0.82],
            fixed_target_weights: vec![16.0, 4.0],
            fixed_target_drs: vec![0.8, 0.9],
            retention_min: 0.7,
            retention_max: 0.95,
            clamp_target: true,
            max_interval_days: None,
        })?;
        let response = fsrs_preset_to_proto(FsrsPreset {
            id: FsrsPresetId::Addon("addon:test".into()),
            name: "Test".into(),
            fsrs_version: FsrsVersion::Seven,
            params: vec![0.0; 21],
            desired_retention: 0.86,
            dynamic_desired_retention: Some(dynamic_dr),
            historical_retention: 0.9,
            ignore_revlogs_before_date: "2024-01-01".into(),
        });

        assert_eq!(response.id, "addon:test");
        assert_eq!(response.fsrs_version, FsrsVersion::Seven as i32);
        assert!(response.fsrs_dynamic_desired_retention_enabled);
        assert_eq!(
            response.fsrs_dynamic_desired_retention_params,
            vec![1.0; 15]
        );
        assert_eq!(
            response.fsrs_dynamic_desired_retention_weights,
            vec![0.0, 15.0]
        );
        assert_eq!(
            response.fsrs_dynamic_desired_retention_avg_drs,
            vec![0.9, 0.8]
        );
        assert_eq!(
            response.fsrs_dynamic_desired_retention_fsrs_eq_weights,
            vec![0.0, 15.0]
        );
        assert_eq!(
            response.fsrs_dynamic_desired_retention_fsrs_eq_drs,
            vec![0.91, 0.82]
        );
        assert_eq!(
            response.fsrs_dynamic_desired_retention_fixed_target_weights,
            vec![16.0, 4.0]
        );
        assert_eq!(
            response.fsrs_dynamic_desired_retention_fixed_target_drs,
            vec![0.8, 0.9]
        );
        assert_eq!(response.fsrs_dynamic_desired_retention_min, 0.7);
        assert_eq!(response.fsrs_dynamic_desired_retention_max, 0.95);
        assert!(response.fsrs_dynamic_desired_retention_clamp);
        Ok(())
    }
}

#[cfg(test)]
mod upstream_tests;
