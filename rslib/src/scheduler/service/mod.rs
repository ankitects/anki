// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod answering;
mod states;

use std::sync::LazyLock;

use anki_proto::cards;
use anki_proto::generic;
use anki_proto::scheduler;
use anki_proto::scheduler::next_card_data_response::AnswerButton;
use anki_proto::scheduler::next_card_data_response::NextCardData;
use anki_proto::scheduler::next_card_data_response::TimerPreferences;
use anki_proto::scheduler::next_card_data_response::TypedAnswer;
use anki_proto::scheduler::ComputeFsrsParamsResponse;
use anki_proto::scheduler::ComputeMemoryStateResponse;
use anki_proto::scheduler::ComputeOptimalRetentionResponse;
use anki_proto::scheduler::FsrsBenchmarkResponse;
use anki_proto::scheduler::FuzzDeltaRequest;
use anki_proto::scheduler::FuzzDeltaResponse;
use anki_proto::scheduler::GetOptimalRetentionParametersResponse;
use anki_proto::scheduler::NextCardDataRequest;
use anki_proto::scheduler::NextCardDataResponse;
use anki_proto::scheduler::SimulateFsrsReviewRequest;
use anki_proto::scheduler::SimulateFsrsReviewResponse;
use anki_proto::scheduler::SimulateFsrsWorkloadResponse;
use fsrs::ComputeParametersInput;
use fsrs::FSRSItem;
use fsrs::FSRSReview;
use fsrs::FSRS;
use regex::Regex;

use crate::backend::Backend;
use crate::card_rendering::service::rendered_nodes_to_proto;
use crate::cloze::extract_cloze_for_typing;
use crate::prelude::*;
use crate::scheduler::fsrs::params::ComputeParamsRequest;
use crate::scheduler::new::NewCardDueOrder;
use crate::scheduler::states::CardState;
use crate::scheduler::states::SchedulingStates;
use crate::search::SortMode;
use crate::services::NotesService;
use crate::stats::studied_today;
use crate::template::RenderedNode;

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
        self.grade_now(&input.card_ids.into_newtype(CardId), input.rating)
            .map(Into::into)
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
        self.answer_card(&mut input.into()).map(Into::into)
    }

    fn upgrade_scheduler(&mut self) -> Result<()> {
        self.transact_no_undo(|col| col.upgrade_to_v2_scheduler())
    }

    fn get_queued_cards(
        &mut self,
        input: scheduler::GetQueuedCardsRequest,
    ) -> Result<scheduler::QueuedCards> {
        self.get_queued_cards(input.fetch_limit as usize, input.intraday_learning_only)
            .map(Into::into)
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
        })
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

    fn evaluate_params(
        &mut self,
        input: scheduler::EvaluateParamsRequest,
    ) -> Result<scheduler::EvaluateParamsResponse> {
        let ret = self.evaluate_params(
            &input.search,
            input.ignore_revlogs_before_ms.into(),
            input.num_of_relearning_steps as usize,
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

    fn fuzz_delta(&mut self, input: FuzzDeltaRequest) -> Result<FuzzDeltaResponse> {
        Ok(FuzzDeltaResponse {
            delta_days: self.get_fuzz_delta(input.card_id.into(), input.interval)?,
        })
    }

    fn next_card_data(&mut self, req: NextCardDataRequest) -> Result<NextCardDataResponse> {
        if let Some(answer) = req.answer {
            self.answer_card(&mut answer.into())?;
        }
        let mut queue = self.get_queued_cards(1, false)?;
        let next_card = queue.cards.first();
        if let Some(next_card) = next_card {
            let cid = next_card.card.id;
            let deck_config = self.deck_config_for_card(&next_card.card)?.inner;
            let note = self.get_note(next_card.card.note_id.into())?;

            let render = self.render_existing_card(cid, false, true)?;
            let show_due = self.get_config_bool(BoolKey::ShowIntervalsAboveAnswerButtons);
            let show_remaning = self.get_config_bool(BoolKey::ShowRemainingDueCountsInStudy);

            let answer_buttons = self
                .describe_next_states(&next_card.states)?
                .into_iter()
                .enumerate()
                .map(|(i, due)| AnswerButton {
                    rating: i as i32,
                    due: if show_due {
                        due
                    } else {
                        "\u{00A0}".to_string() /* &nbsp */
                    },
                })
                .collect();

            // Typed answer replacements
            static ANSWER_REGEX: LazyLock<Regex> =
                LazyLock::new(|| Regex::new(r"\[\[type:(.+?:)?(.+?)\]\]").unwrap());

            const ANSWER_HTML: &str = "<center>
<input type=text id=typeans onkeypress=\"_typeAnsPress();\"
   style=\"font-family: '{self.typeFont}'; font-size: {self.typeSize}px;\">
</center>";

            let mut q_nodes = render.qnodes;
            let typed_answer_parent_node = q_nodes.iter_mut().find_map(|node| {
                if let RenderedNode::Text { text } = node {
                    let mut out = None;
                    *text = ANSWER_REGEX
                        .replace(text, |cap: &regex::Captures<'_>| {
                            out = Some((
                                cap.get(1)
                                    .map(|g| g.as_str().to_string())
                                    .unwrap_or("".to_string()),
                                cap[2].to_string(),
                            ));
                            ANSWER_HTML
                        })
                        .to_string();
                    out
                } else {
                    None
                }
            });

            let typed_answer = typed_answer_parent_node
                .map(|field| -> Result<(String, String)> {
                    let notetype = self
                        .get_notetype(note.notetype_id.into())?
                        .or_not_found(note.notetype_id)?;
                    let field_ord = notetype.get_field_ord(&field.1).or_not_found(field.1)?;
                    let mut correct = note.fields[field_ord].clone();
                    if field.0.contains("cloze") {
                        let card_ord = queue.cards[0].card.template_idx;
                        correct = extract_cloze_for_typing(&correct, card_ord + 1).to_string()
                    }
                    Ok((field.0, correct))
                })
                .transpose()?;

            let marked = note.tags.contains(&"marked".to_string());

            if !show_remaning {
                queue.learning_count = 0;
                queue.review_count = 0;
                queue.new_count = 0;
            }

            let timer = deck_config.show_timer.then_some(TimerPreferences {
                max_time_ms: deck_config.cap_answer_time_to_secs * 1000,
                stop_on_answer: deck_config.stop_timer_on_answer,
            });

            Ok(NextCardDataResponse {
                next_card: Some(NextCardData {
                    queue: Some(queue.into()),

                    css: render.css.clone(),
                    partial_front: rendered_nodes_to_proto(q_nodes),
                    partial_back: rendered_nodes_to_proto(render.anodes),

                    answer_buttons,
                    autoplay: !deck_config.disable_autoplay,
                    typed_answer: typed_answer.map(|answer| TypedAnswer {
                        text: answer.1,
                        args: answer.0,
                    }),
                    marked,
                    timer,

                    // Filled by python
                    front: "".to_string(),
                    back: "".to_string(),
                    body_class: "".to_string(),
                    question_av_tags: vec![],
                    answer_av_tags: vec![],
                }),
            })
        } else {
            Ok(NextCardDataResponse::default())
        }
    }
}

impl crate::services::BackendSchedulerService for Backend {
    fn compute_fsrs_params_from_items(
        &self,
        req: scheduler::ComputeFsrsParamsFromItemsRequest,
    ) -> Result<scheduler::ComputeFsrsParamsResponse> {
        let fsrs = FSRS::new(None)?;
        let fsrs_items = req.items.len() as u32;
        let params = fsrs.compute_parameters(ComputeParametersInput {
            train_set: req.items.into_iter().map(fsrs_item_proto_to_fsrs).collect(),
            progress: None,
            enable_short_term: true,
            num_relearning_steps: None,
        })?;
        Ok(ComputeFsrsParamsResponse {
            params,
            fsrs_items,
            health_check_passed: None,
        })
    }

    fn fsrs_benchmark(
        &self,
        req: scheduler::FsrsBenchmarkRequest,
    ) -> Result<scheduler::FsrsBenchmarkResponse> {
        let fsrs = FSRS::new(None)?;
        let train_set = req
            .train_set
            .into_iter()
            .map(fsrs_item_proto_to_fsrs)
            .collect();
        let params = fsrs.benchmark(ComputeParametersInput {
            train_set,
            progress: None,
            enable_short_term: true,
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

fn fsrs_review_proto_to_fsrs(review: anki_proto::scheduler::FsrsReview) -> FSRSReview {
    FSRSReview {
        delta_t: review.delta_t,
        rating: review.rating,
    }
}
