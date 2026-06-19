// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod answering;
mod states;

use anki_proto::cards;
use anki_proto::generic;
use anki_proto::scheduler;
use anki_proto::scheduler::ComputeFsrsParamsResponse;
use anki_proto::scheduler::ComputeMemoryStateResponse;
use anki_proto::scheduler::ComputeOptimalRetentionResponse;
use anki_proto::scheduler::FsrsBenchmarkResponse;
use anki_proto::scheduler::FuzzDeltaRequest;
use anki_proto::scheduler::FuzzDeltaResponse;
use anki_proto::scheduler::GetOptimalRetentionParametersResponse;
use anki_proto::scheduler::SimulateFsrsReviewRequest;
use anki_proto::scheduler::SimulateFsrsReviewResponse;
use anki_proto::scheduler::SimulateFsrsWorkloadResponse;
use fsrs::ComputeParametersInput;
use fsrs::FSRSItem;
use fsrs::FSRSReview;
use fsrs::FSRS;

use crate::backend::Backend;
use crate::prelude::*;
use crate::scheduler::fsrs::params::ComputeParamsRequest;
use crate::scheduler::new::NewCardDueOrder;
use crate::scheduler::states::CardState;
use crate::scheduler::states::SchedulingStates;
use crate::search::SortMode;
use crate::stats::studied_today;

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

#[cfg(test)]
mod tests {
    use anki_proto::scheduler::card_answer::Rating;
    use anki_proto::scheduler::CardAnswer;
    use anki_proto::scheduler::GetQueuedCardsRequest;

    use crate::card::CardQueue;
    use crate::card::CardType;
    use crate::prelude::*;
    use crate::services::SchedulerService;
    use crate::tests::NoteAdder;

    /// Answers the top queued card through the SchedulerService trait with the
    /// given rating, returning the answered card's id. Building the request from
    /// `get_queued_cards` mirrors what the front-end does.
    fn answer_top_card(col: &mut Collection, rating: Rating) -> i64 {
        let queued = SchedulerService::get_queued_cards(
            col,
            GetQueuedCardsRequest {
                fetch_limit: 1,
                intraday_learning_only: false,
            },
        )
        .unwrap();
        let qc = queued.cards.first().expect("a card should be queued");
        let card_id = qc.card.as_ref().unwrap().id;
        let states = qc.states.clone().unwrap();
        let new_state = match rating {
            Rating::Again => states.again,
            Rating::Hard => states.hard,
            Rating::Good => states.good,
            Rating::Easy => states.easy,
        };
        let _ = SchedulerService::answer_card(
            col,
            CardAnswer {
                card_id,
                current_state: states.current,
                new_state,
                rating: rating as i32,
                answered_at_millis: TimestampMillis::now().0,
                milliseconds_taken: 0,
            },
        )
        .unwrap();
        card_id
    }

    #[test]
    fn answer_again_moves_new_card_to_learn_queue() {
        let mut col = Collection::new();
        NoteAdder::basic(&mut col).add(&mut col);

        let card_id = answer_top_card(&mut col, Rating::Again);

        let card = col.storage.get_card(CardId(card_id)).unwrap().unwrap();
        assert_eq!(card.queue, CardQueue::Learn, "Again should send the card to learning");
        assert_eq!(card.ctype, CardType::Learn);
    }

    #[test]
    fn answer_hard_keeps_new_card_in_learning_with_longer_delay_than_again() {
        // Hard stays on the same learning step but applies a longer delay than
        // Again: for the default steps [1min, 10min], Again uses step-1 (60s)
        // while Hard uses the average of step-1 and step-2 (330s).
        let (due_again, queue_again, ctype_again) = {
            let mut col = Collection::new();
            NoteAdder::basic(&mut col).add(&mut col);
            let cid = CardId(answer_top_card(&mut col, Rating::Again));
            let card = col.storage.get_card(cid).unwrap().unwrap();
            (card.due, card.queue, card.ctype)
        };

        let (due_hard, queue_hard, ctype_hard) = {
            let mut col = Collection::new();
            NoteAdder::basic(&mut col).add(&mut col);
            let cid = CardId(answer_top_card(&mut col, Rating::Hard));
            let card = col.storage.get_card(cid).unwrap().unwrap();
            (card.due, card.queue, card.ctype)
        };

        assert_eq!(queue_again, CardQueue::Learn);
        assert_eq!(ctype_again, CardType::Learn);
        assert_eq!(queue_hard, CardQueue::Learn);
        assert_eq!(ctype_hard, CardType::Learn);
        assert!(
            due_hard > due_again,
            "Hard delay ({due_hard}s) should be longer than Again delay ({due_again}s)"
        );
    }

    #[test]
    fn answer_good_repeatedly_graduates_card_through_learning_steps() {
        let mut col = Collection::new();
        NoteAdder::basic(&mut col).add(&mut col);

        // First Good: new -> learning (advances one step, stays in Learn).
        let card_id = answer_top_card(&mut col, Rating::Good);
        let card = col.storage.get_card(CardId(card_id)).unwrap().unwrap();
        assert_eq!(card.queue, CardQueue::Learn, "first Good keeps the card in learning");
        assert_eq!(card.ctype, CardType::Learn);

        // Force the learning card to be due now so it re-enters the queue,
        // then answer Good again to graduate it.
        col.storage
            .db
            .execute_batch("UPDATE cards SET due = 0")
            .unwrap();
        col.clear_study_queues();

        answer_top_card(&mut col, Rating::Good);
        let card = col.storage.get_card(CardId(card_id)).unwrap().unwrap();
        assert_eq!(card.queue, CardQueue::Review, "second Good graduates the card");
        assert_eq!(card.ctype, CardType::Review);
    }

    #[test]
    fn answer_easy_graduates_new_card_to_review_queue() {
        let mut col = Collection::new();
        NoteAdder::basic(&mut col).add(&mut col);

        let card_id = answer_top_card(&mut col, Rating::Easy);

        let card = col.storage.get_card(CardId(card_id)).unwrap().unwrap();
        assert_eq!(
            card.queue,
            CardQueue::Review,
            "Easy on a new card should graduate it straight to review"
        );
        assert_eq!(card.ctype, CardType::Review);
        assert!(card.interval >= 1, "graduated card should have a review interval");
    }

    #[test]
    fn new_card_appears_in_new_queue() {
        let mut col = Collection::new();
        let note = NoteAdder::basic(&mut col).add(&mut col);

        let queued = SchedulerService::get_queued_cards(
            &mut col,
            GetQueuedCardsRequest {
                fetch_limit: 10,
                intraday_learning_only: false,
            },
        )
        .unwrap();

        assert_eq!(queued.new_count, 1, "the freshly added card should be counted as new");
        assert_eq!(queued.learning_count, 0);
        assert_eq!(queued.review_count, 0);
        assert_eq!(queued.cards.len(), 1);
        // the queued card belongs to the note we just added
        let queued_card = queued.cards[0].card.as_ref().unwrap();
        let expected_cid = col.storage.card_ids_of_notes(&[note.id]).unwrap()[0];
        assert_eq!(queued_card.id, expected_cid.0);
    }

    #[test]
    fn get_scheduling_states_returns_all_rating_states_for_new_card() {
        let mut col = Collection::new();
        let note = NoteAdder::basic(&mut col).add(&mut col);
        let cid = col.storage.card_ids_of_notes(&[note.id]).unwrap()[0];

        let states = SchedulerService::get_scheduling_states(
            &mut col,
            anki_proto::cards::CardId { cid: cid.0 },
        )
        .unwrap();

        // A new card must expose a current state plus the four rating previews.
        assert!(states.current.is_some(), "current state should be populated");
        assert!(states.again.is_some());
        assert!(states.hard.is_some());
        assert!(states.good.is_some());
        assert!(states.easy.is_some());
    }

    #[test]
    fn bury_user_removes_card_from_queue() {
        use anki_proto::scheduler::bury_or_suspend_cards_request::Mode;
        let mut col = Collection::new();
        let note = NoteAdder::basic(&mut col).add(&mut col);
        let cid = col.storage.card_ids_of_notes(&[note.id]).unwrap()[0];

        let out = SchedulerService::bury_or_suspend_cards(
            &mut col,
            anki_proto::scheduler::BuryOrSuspendCardsRequest {
                card_ids: vec![cid.0],
                note_ids: vec![],
                mode: Mode::BuryUser as i32,
            },
        )
        .unwrap();
        assert_eq!(out.count, 1, "one card should have been buried");

        let card = col.storage.get_card(cid).unwrap().unwrap();
        assert_eq!(card.queue, CardQueue::UserBuried);
        // a buried card must not appear in the study queue
        assert_eq!(col.counts(), [0, 0, 0], "buried card should leave the queue");
    }

    #[test]
    fn restore_buried_brings_card_back_to_queue() {
        use anki_proto::scheduler::bury_or_suspend_cards_request::Mode;
        let mut col = Collection::new();
        let note = NoteAdder::basic(&mut col).add(&mut col);
        let cid = col.storage.card_ids_of_notes(&[note.id]).unwrap()[0];

        // bury first
        let _ = SchedulerService::bury_or_suspend_cards(
            &mut col,
            anki_proto::scheduler::BuryOrSuspendCardsRequest {
                card_ids: vec![cid.0],
                note_ids: vec![],
                mode: Mode::BuryUser as i32,
            },
        )
        .unwrap();
        assert_eq!(col.counts(), [0, 0, 0], "precondition: card is buried");

        // restore it
        let _ = SchedulerService::restore_buried_and_suspended_cards(
            &mut col,
            anki_proto::cards::CardIds { cids: vec![cid.0] },
        )
        .unwrap();

        let card = col.storage.get_card(cid).unwrap().unwrap();
        assert_eq!(card.queue, CardQueue::New, "restored card returns to the new queue");
        assert_eq!(col.counts(), [1, 0, 0], "card is queryable again");
    }

    #[test]
    fn unbury_deck_unburies_cards_in_that_deck() {
        use anki_proto::scheduler::bury_or_suspend_cards_request::Mode as BuryMode;
        use anki_proto::scheduler::unbury_deck_request::Mode as UnburyMode;
        let mut col = Collection::new();
        let note = NoteAdder::basic(&mut col).add(&mut col);
        let cid = col.storage.card_ids_of_notes(&[note.id]).unwrap()[0];

        // bury the card (it lives in the default deck, id 1)
        let _ = SchedulerService::bury_or_suspend_cards(
            &mut col,
            anki_proto::scheduler::BuryOrSuspendCardsRequest {
                card_ids: vec![cid.0],
                note_ids: vec![],
                mode: BuryMode::BuryUser as i32,
            },
        )
        .unwrap();
        assert_eq!(col.counts(), [0, 0, 0], "precondition: card is buried");

        // unbury the whole default deck
        let _ = SchedulerService::unbury_deck(
            &mut col,
            anki_proto::scheduler::UnburyDeckRequest {
                deck_id: 1,
                mode: UnburyMode::All as i32,
            },
        )
        .unwrap();

        let card = col.storage.get_card(cid).unwrap().unwrap();
        assert_eq!(card.queue, CardQueue::New, "card in the deck is unburied");
        assert_eq!(col.counts(), [1, 0, 0]);
    }

    #[test]
    fn suspend_marks_card_as_suspended_and_removes_from_queue() {
        use anki_proto::scheduler::bury_or_suspend_cards_request::Mode;
        let mut col = Collection::new();
        let note = NoteAdder::basic(&mut col).add(&mut col);
        let cid = col.storage.card_ids_of_notes(&[note.id]).unwrap()[0];

        let out = SchedulerService::bury_or_suspend_cards(
            &mut col,
            anki_proto::scheduler::BuryOrSuspendCardsRequest {
                card_ids: vec![cid.0],
                note_ids: vec![],
                mode: Mode::Suspend as i32,
            },
        )
        .unwrap();
        assert_eq!(out.count, 1);

        let card = col.storage.get_card(cid).unwrap().unwrap();
        assert_eq!(card.queue, CardQueue::Suspended);
        assert_eq!(col.counts(), [0, 0, 0], "suspended card leaves the queue");
    }

    #[test]
    fn bury_via_note_ids_resolves_cards_from_notes() {
        use anki_proto::scheduler::bury_or_suspend_cards_request::Mode;
        let mut col = Collection::new();
        let note = NoteAdder::basic(&mut col).add(&mut col);
        let cid = col.storage.card_ids_of_notes(&[note.id]).unwrap()[0];

        // pass note_ids and leave card_ids empty to exercise the
        // card_ids_of_notes resolution branch in the service method
        let out = SchedulerService::bury_or_suspend_cards(
            &mut col,
            anki_proto::scheduler::BuryOrSuspendCardsRequest {
                card_ids: vec![],
                note_ids: vec![note.id.0],
                mode: Mode::BuryUser as i32,
            },
        )
        .unwrap();
        assert_eq!(out.count, 1, "the note's card should be buried");

        let card = col.storage.get_card(cid).unwrap().unwrap();
        assert_eq!(card.queue, CardQueue::UserBuried);
    }

    #[test]
    fn bury_sched_marks_card_as_sched_buried_not_user_buried() {
        use anki_proto::scheduler::bury_or_suspend_cards_request::Mode;
        let mut col = Collection::new();
        let note = NoteAdder::basic(&mut col).add(&mut col);
        let cid = col.storage.card_ids_of_notes(&[note.id]).unwrap()[0];

        let out = SchedulerService::bury_or_suspend_cards(
            &mut col,
            anki_proto::scheduler::BuryOrSuspendCardsRequest {
                card_ids: vec![cid.0],
                note_ids: vec![],
                mode: Mode::BurySched as i32,
            },
        )
        .unwrap();
        assert_eq!(out.count, 1);

        let card = col.storage.get_card(cid).unwrap().unwrap();
        // SchedBuried (-2) is the sibling-auto-bury queue; it must not be
        // conflated with UserBuried (-3) because they have separate recovery
        // paths (unbury_deck SchedOnly vs UserOnly).
        assert_eq!(card.queue, CardQueue::SchedBuried);
        assert_eq!(col.counts(), [0, 0, 0], "sched-buried card leaves the queue");

        // congrats_info must report sched_buried, not user_buried
        let info = SchedulerService::congrats_info(&mut col).unwrap();
        assert!(info.have_sched_buried);
        assert!(!info.have_user_buried);
    }

    #[test]
    fn schedule_cards_as_new_resets_graduated_card() {
        let mut col = Collection::new();
        NoteAdder::basic(&mut col).add(&mut col);

        // graduate the card to review first
        let card_id = answer_top_card(&mut col, Rating::Easy);
        let card = col.storage.get_card(CardId(card_id)).unwrap().unwrap();
        assert_eq!(card.ctype, CardType::Review, "precondition: card graduated");

        // reset it back to new
        let _ = SchedulerService::schedule_cards_as_new(
            &mut col,
            anki_proto::scheduler::ScheduleCardsAsNewRequest {
                card_ids: vec![card_id],
                log: true,
                restore_position: true,
                reset_counts: false,
                context: None,
            },
        )
        .unwrap();

        let card = col.storage.get_card(CardId(card_id)).unwrap().unwrap();
        assert_eq!(card.ctype, CardType::New, "card should be reset to new");
        assert_eq!(card.queue, CardQueue::New);
    }

    #[test]
    fn schedule_cards_as_new_accepts_explicit_context() {
        use anki_proto::scheduler::schedule_cards_as_new_request::Context;
        let mut col = Collection::new();
        NoteAdder::basic(&mut col).add(&mut col);

        let card_id = answer_top_card(&mut col, Rating::Easy);

        // passing an explicit context exercises the context-decoding branch
        let _ = SchedulerService::schedule_cards_as_new(
            &mut col,
            anki_proto::scheduler::ScheduleCardsAsNewRequest {
                card_ids: vec![card_id],
                log: true,
                restore_position: true,
                reset_counts: false,
                context: Some(Context::Browser as i32),
            },
        )
        .unwrap();

        let card = col.storage.get_card(CardId(card_id)).unwrap().unwrap();
        assert_eq!(card.ctype, CardType::New, "card should be reset to new");
    }

    #[test]
    fn schedule_cards_as_new_defaults_returns_for_both_contexts() {
        use anki_proto::scheduler::schedule_cards_as_new_request::Context;
        let mut col = Collection::new();

        // both contexts should resolve to a defaults response without error
        for context in [Context::Browser, Context::Reviewer] {
            let resp = SchedulerService::schedule_cards_as_new_defaults(
                &mut col,
                anki_proto::scheduler::ScheduleCardsAsNewDefaultsRequest {
                    context: context as i32,
                },
            )
            .unwrap();
            // restore_position default is true in a fresh collection
            assert!(resp.restore_position);
        }
    }

    #[test]
    fn set_due_date_moves_card_to_review_with_given_offset() {
        let mut col = Collection::new();
        let note = NoteAdder::basic(&mut col).add(&mut col);
        let cid = col.storage.card_ids_of_notes(&[note.id]).unwrap()[0];

        // schedule the (new) card to be due in 3 days
        let _ = SchedulerService::set_due_date(
            &mut col,
            anki_proto::scheduler::SetDueDateRequest {
                card_ids: vec![cid.0],
                days: "3".to_string(),
                config_key: None,
            },
        )
        .unwrap();

        let card = col.storage.get_card(cid).unwrap().unwrap();
        assert_eq!(card.ctype, CardType::Review, "set_due_date turns the card into a review card");
        assert_eq!(card.queue, CardQueue::Review);
        // a new card scheduled "3" days out gets an interval of 3 days
        assert_eq!(card.interval, 3, "interval should match the requested offset");
    }

    #[test]
    fn set_due_date_with_config_key_persists_the_history() {
        use anki_proto::config::config_key::String as StringConfigKey;
        let mut col = Collection::new();
        let note = NoteAdder::basic(&mut col).add(&mut col);
        let cid = col.storage.card_ids_of_notes(&[note.id]).unwrap()[0];

        // providing a config_key exercises the closure that maps it to a config
        // key and persists the entered value as the new default.
        let _ = SchedulerService::set_due_date(
            &mut col,
            anki_proto::scheduler::SetDueDateRequest {
                card_ids: vec![cid.0],
                days: "5".to_string(),
                config_key: Some(anki_proto::config::OptionalStringConfigKey {
                    key: StringConfigKey::SetDueBrowser as i32,
                }),
            },
        )
        .unwrap();

        let card = col.storage.get_card(cid).unwrap().unwrap();
        assert_eq!(card.ctype, CardType::Review);
        assert_eq!(card.interval, 5, "the requested offset is applied");
    }

    #[test]
    fn grade_now_easy_graduates_card_by_id() {
        let mut col = Collection::new();
        let note = NoteAdder::basic(&mut col).add(&mut col);
        let cid = col.storage.card_ids_of_notes(&[note.id]).unwrap()[0];

        // grade the card directly by id, without pulling it from the queue
        let _ = SchedulerService::grade_now(
            &mut col,
            anki_proto::scheduler::GradeNowRequest {
                card_ids: vec![cid.0],
                rating: Rating::Easy as i32,
            },
        )
        .unwrap();

        let card = col.storage.get_card(cid).unwrap().unwrap();
        assert_eq!(card.ctype, CardType::Review, "Easy grade_now graduates the card");
        assert_eq!(card.queue, CardQueue::Review);
    }

    #[test]
    fn sort_cards_repositions_new_cards_from_starting_offset() {
        let mut col = Collection::new();
        let note1 = NoteAdder::basic(&mut col).add(&mut col);
        let note2 = NoteAdder::basic(&mut col).fields(&["b", ""]).add(&mut col);
        let cid1 = col.storage.card_ids_of_notes(&[note1.id]).unwrap()[0];
        let cid2 = col.storage.card_ids_of_notes(&[note2.id]).unwrap()[0];

        // reposition the two new cards starting at position 5, step 1, preserving order
        let out = SchedulerService::sort_cards(
            &mut col,
            anki_proto::scheduler::SortCardsRequest {
                card_ids: vec![cid1.0, cid2.0],
                starting_from: 5,
                step_size: 1,
                randomize: false,
                shift_existing: false,
            },
        )
        .unwrap();
        assert_eq!(out.count, 2, "both cards should be repositioned");

        // new card position is stored in `due`; order preserved => 5 then 6
        let card1 = col.storage.get_card(cid1).unwrap().unwrap();
        let card2 = col.storage.get_card(cid2).unwrap().unwrap();
        assert_eq!(card1.due, 5);
        assert_eq!(card2.due, 6);
    }

    #[test]
    fn sort_cards_with_randomize_produces_different_ordering_than_preserve() {
        // With N=8 cards the probability that a random shuffle happens to match
        // the preserve ordering exactly is 1/8! ≈ 1/40_000, an acceptable
        // flake risk. Two cards cannot distinguish the two orderings at all.
        const N: usize = 8;
        let mut col = Collection::new();

        let cids: Vec<CardId> = (0..N)
            .map(|i| {
                let note = NoteAdder::basic(&mut col)
                    .fields(&[&i.to_string(), ""])
                    .add(&mut col);
                col.storage.card_ids_of_notes(&[note.id]).unwrap()[0]
            })
            .collect();
        let cid_ints: Vec<i64> = cids.iter().map(|c| c.0).collect();

        // Establish the deterministic baseline: cids[i].due == i.
        let _ = SchedulerService::sort_cards(
            &mut col,
            anki_proto::scheduler::SortCardsRequest {
                card_ids: cid_ints.clone(),
                starting_from: 0,
                step_size: 1,
                randomize: false,
                shift_existing: false,
            },
        )
        .unwrap();
        let preserve_dues: Vec<i32> = cids
            .iter()
            .map(|&id| col.storage.get_card(id).unwrap().unwrap().due)
            .collect();

        // Apply randomized sort and collect the resulting positions.
        let out = SchedulerService::sort_cards(
            &mut col,
            anki_proto::scheduler::SortCardsRequest {
                card_ids: cid_ints.clone(),
                starting_from: 0,
                step_size: 1,
                randomize: true,
                shift_existing: false,
            },
        )
        .unwrap();
        assert_eq!(out.count, N as u32);

        let random_dues: Vec<i32> = cids
            .iter()
            .map(|&id| col.storage.get_card(id).unwrap().unwrap().due)
            .collect();

        // Every card must receive a unique position within [0, N).
        let mut sorted = random_dues.clone();
        sorted.sort_unstable();
        assert_eq!(
            sorted,
            (0..N as i32).collect::<Vec<_>>(),
            "positions must be a permutation of [0, N)"
        );

        // The random ordering must differ from the preserve ordering.
        assert_ne!(
            random_dues, preserve_dues,
            "randomized sort should produce a different ordering than preserve"
        );
    }

    #[test]
    fn sort_deck_repositions_new_cards_in_deck() {
        let mut col = Collection::new();
        NoteAdder::basic(&mut col).add(&mut col);
        NoteAdder::basic(&mut col).fields(&["b", ""]).add(&mut col);

        let out = SchedulerService::sort_deck(
            &mut col,
            anki_proto::scheduler::SortDeckRequest {
                deck_id: 1,
                randomize: false,
            },
        )
        .unwrap();

        assert_eq!(out.count, 2, "both new cards in the deck are repositioned");
    }

    #[test]
    fn reposition_defaults_returns_stored_values() {
        let mut col = Collection::new();
        // smoke test: the call resolves the reposition dialog defaults without error
        let resp = SchedulerService::reposition_defaults(&mut col).unwrap();
        // a fresh collection defaults to non-random repositioning
        assert!(!resp.random);
    }

    #[test]
    fn describe_next_states_returns_one_label_per_button() {
        let mut col = Collection::new();
        let note = NoteAdder::basic(&mut col).add(&mut col);
        let cid = col.storage.card_ids_of_notes(&[note.id]).unwrap()[0];

        let states = SchedulerService::get_scheduling_states(
            &mut col,
            anki_proto::cards::CardId { cid: cid.0 },
        )
        .unwrap();

        let labels = SchedulerService::describe_next_states(&mut col, states).unwrap();

        // one human-readable interval label for Again/Hard/Good/Easy
        assert_eq!(labels.vals.len(), 4);
        assert!(labels.vals.iter().all(|s| !s.is_empty()));
    }

    #[test]
    fn state_is_leech_reflects_review_leeched_flag() {
        use anki_proto::scheduler::scheduling_state;

        let mut col = Collection::new();

        // Only `leeched` is relevant to state_is_leech; the remaining fields are
        // required to build a valid Review state but do not affect the result.
        let review_state = |leeched: bool| scheduling_state::Review {
            scheduled_days: 0,
            elapsed_days: 0,
            ease_factor: 0.0,
            lapses: 0,
            leeched,
            memory_state: None,
        };
        let normal_review = |leeched: bool| anki_proto::scheduler::SchedulingState {
            kind: Some(scheduling_state::Kind::Normal(scheduling_state::Normal {
                kind: Some(scheduling_state::normal::Kind::Review(review_state(leeched))),
            })),
            custom_data: None,
        };

        let leech = SchedulerService::state_is_leech(&mut col, normal_review(true)).unwrap();
        assert!(leech.val, "a review state flagged as leeched is a leech");

        let not_leech = SchedulerService::state_is_leech(&mut col, normal_review(false)).unwrap();
        assert!(!not_leech.val, "a review state without the flag is not a leech");
    }

    #[test]
    fn sched_timing_today_reports_no_elapsed_days_and_future_rollover() {
        let mut col = Collection::new();

        let timing = SchedulerService::sched_timing_today(&mut col).unwrap();

        // A collection created today has not rolled over to a new day yet.
        assert_eq!(timing.days_elapsed, 0);
        // The next day-rollover must be scheduled in the future.
        assert!(timing.next_day_at > TimestampSecs::now().0);
    }

    #[test]
    fn studied_today_reports_nothing_studied_for_fresh_collection() {
        let mut col = Collection::new();

        let msg = SchedulerService::studied_today(&mut col).unwrap().val;

        assert_eq!(
            msg.replace('\n', " "),
            "Studied 0 cards in 0 seconds today (0s/card)"
        );
    }

    #[test]
    fn studied_today_message_renders_counts_from_request() {
        let mut col = Collection::new();

        let msg = SchedulerService::studied_today_message(
            &mut col,
            anki_proto::scheduler::StudiedTodayMessageRequest {
                cards: 3,
                seconds: 13.0,
            },
        )
        .unwrap()
        .val;

        // template_only i18n renders a deterministic, English summary string.
        assert_eq!(
            msg.replace('\n', " "),
            "Studied 3 cards in 13 seconds today (4.33s/card)"
        );
    }

    #[test]
    fn counts_for_deck_today_tracks_new_cards_studied() {
        let mut col = Collection::new();
        NoteAdder::basic(&mut col).add(&mut col);

        // Nothing studied yet in the default deck.
        let before = SchedulerService::counts_for_deck_today(
            &mut col,
            anki_proto::decks::DeckId { did: 1 },
        )
        .unwrap();
        assert_eq!(before.new, 0);
        assert_eq!(before.review, 0);

        // Answering the new card counts it as one new card studied today.
        answer_top_card(&mut col, Rating::Good);

        let after = SchedulerService::counts_for_deck_today(
            &mut col,
            anki_proto::decks::DeckId { did: 1 },
        )
        .unwrap();
        assert_eq!(after.new, 1, "studying a new card increments the new count");
    }

    #[test]
    fn congrats_info_reports_nothing_remaining_for_empty_collection() {
        let mut col = Collection::new();

        let info = SchedulerService::congrats_info(&mut col).unwrap();

        // An empty default deck has no work left and is not a filtered deck.
        assert!(!info.new_remaining);
        assert!(!info.review_remaining);
        assert_eq!(info.learn_remaining, 0);
        assert!(!info.is_filtered_deck);
    }

    #[test]
    fn congrats_info_shows_new_remaining_when_new_cards_exist() {
        let mut col = Collection::new();
        NoteAdder::basic(&mut col).add(&mut col);

        let info = SchedulerService::congrats_info(&mut col).unwrap();

        assert!(info.new_remaining, "new card should be reported as remaining");
        assert!(!info.review_remaining);
        assert_eq!(info.learn_remaining, 0);
    }

    #[test]
    fn congrats_info_shows_review_remaining_when_review_card_is_due_today() {
        let mut col = Collection::new();
        let note = NoteAdder::basic(&mut col).add(&mut col);
        let cid = col.storage.card_ids_of_notes(&[note.id]).unwrap()[0];

        // days="0" schedules the card as a review card due today (offset 0 from
        // the collection's days_elapsed, which is 0 for a fresh collection).
        let _ = SchedulerService::set_due_date(
            &mut col,
            anki_proto::scheduler::SetDueDateRequest {
                card_ids: vec![cid.0],
                days: "0".to_string(),
                config_key: None,
            },
        )
        .unwrap();

        let info = SchedulerService::congrats_info(&mut col).unwrap();

        assert!(info.review_remaining, "review card due today should be reported");
        assert!(!info.new_remaining, "card is no longer in the new queue");
    }

    #[test]
    fn congrats_info_shows_have_user_buried_when_card_is_buried() {
        use anki_proto::scheduler::bury_or_suspend_cards_request::Mode;
        let mut col = Collection::new();
        let note = NoteAdder::basic(&mut col).add(&mut col);
        let cid = col.storage.card_ids_of_notes(&[note.id]).unwrap()[0];

        let _ = SchedulerService::bury_or_suspend_cards(
            &mut col,
            anki_proto::scheduler::BuryOrSuspendCardsRequest {
                card_ids: vec![cid.0],
                note_ids: vec![],
                mode: Mode::BuryUser as i32,
            },
        )
        .unwrap();

        let info = SchedulerService::congrats_info(&mut col).unwrap();

        assert!(info.have_user_buried, "user-buried card should be reported");
        assert!(!info.new_remaining, "buried card is no longer in the new queue");
    }

    #[test]
    fn update_stats_adds_deltas_to_deck_counts() {
        let mut col = Collection::new();

        SchedulerService::update_stats(
            &mut col,
            anki_proto::scheduler::UpdateStatsRequest {
                deck_id: 1,
                new_delta: 2,
                review_delta: 3,
                millisecond_delta: 0,
            },
        )
        .unwrap();

        let counts = SchedulerService::counts_for_deck_today(
            &mut col,
            anki_proto::decks::DeckId { did: 1 },
        )
        .unwrap();
        assert_eq!(counts.new, 2, "new_delta is added to the studied count");
        assert_eq!(counts.review, 3, "review_delta is added to the studied count");
    }

    #[test]
    fn extend_limits_subtracts_from_studied_counts() {
        let mut col = Collection::new();

        // Pretend 5 new / 5 review cards were already studied today.
        SchedulerService::update_stats(
            &mut col,
            anki_proto::scheduler::UpdateStatsRequest {
                deck_id: 1,
                new_delta: 5,
                review_delta: 5,
                millisecond_delta: 0,
            },
        )
        .unwrap();

        // Extending the limits lowers the studied counts, freeing up more cards.
        SchedulerService::extend_limits(
            &mut col,
            anki_proto::scheduler::ExtendLimitsRequest {
                deck_id: 1,
                new_delta: 2,
                review_delta: 1,
            },
        )
        .unwrap();

        let counts = SchedulerService::counts_for_deck_today(
            &mut col,
            anki_proto::decks::DeckId { did: 1 },
        )
        .unwrap();
        assert_eq!(counts.new, 3, "5 studied minus a limit extension of 2");
        assert_eq!(counts.review, 4, "5 studied minus a limit extension of 1");
    }

    #[test]
    fn upgrade_scheduler_switches_collection_to_v2() {
        let mut col = Collection::new();

        SchedulerService::upgrade_scheduler(&mut col).unwrap();

        assert!(col.v2_enabled(), "scheduler should report v2 after upgrade");
    }

    /// Adds a learning card that the default filtered-deck search will gather.
    fn add_gatherable_card(col: &mut Collection) -> Card {
        let mut card = Card {
            deck_id: DeckId(1),
            ctype: CardType::Learn,
            queue: CardQueue::DayLearn,
            remaining_steps: 1,
            due: 0,
            ..Default::default()
        };
        col.add_card(&mut card).unwrap();
        card
    }

    #[test]
    fn rebuild_filtered_deck_gathers_matching_cards() {
        let mut col = Collection::new();
        let card = add_gatherable_card(&mut col);

        let mut filtered = Deck::new_filtered();
        col.add_or_update_deck(&mut filtered).unwrap();

        let out = SchedulerService::rebuild_filtered_deck(
            &mut col,
            anki_proto::decks::DeckId { did: filtered.id.0 },
        )
        .unwrap();

        assert_eq!(out.count, 1, "the matching card is gathered");
        let card = col.storage.get_card(card.id).unwrap().unwrap();
        assert_eq!(card.deck_id, filtered.id, "card now lives in the filtered deck");
    }

    #[test]
    fn empty_filtered_deck_returns_cards_to_home_deck() {
        let mut col = Collection::new();
        let card = add_gatherable_card(&mut col);

        let mut filtered = Deck::new_filtered();
        col.add_or_update_deck(&mut filtered).unwrap();
        col.rebuild_filtered_deck(filtered.id).unwrap();
        // sanity: the card was pulled into the filtered deck
        assert_eq!(col.storage.get_card(card.id).unwrap().unwrap().deck_id, filtered.id);

        let _ = SchedulerService::empty_filtered_deck(
            &mut col,
            anki_proto::decks::DeckId { did: filtered.id.0 },
        )
        .unwrap();

        let card = col.storage.get_card(card.id).unwrap().unwrap();
        assert_eq!(card.deck_id, DeckId(1), "card is returned to its home deck");
    }

    #[test]
    fn custom_study_extends_new_limit() {
        let mut col = Collection::new();

        let _ = SchedulerService::custom_study(
            &mut col,
            anki_proto::scheduler::CustomStudyRequest {
                deck_id: 1,
                value: Some(anki_proto::scheduler::custom_study_request::Value::NewLimitDelta(5)),
            },
        )
        .unwrap();

        let defaults = SchedulerService::custom_study_defaults(
            &mut col,
            anki_proto::scheduler::CustomStudyDefaultsRequest { deck_id: 1 },
        )
        .unwrap();
        assert_eq!(defaults.extend_new, 5, "new limit was extended by the delta");
    }

    #[test]
    fn custom_study_defaults_reports_available_new_cards() {
        let mut col = Collection::new();
        NoteAdder::basic(&mut col).add(&mut col);

        let defaults = SchedulerService::custom_study_defaults(
            &mut col,
            anki_proto::scheduler::CustomStudyDefaultsRequest { deck_id: 1 },
        )
        .unwrap();

        assert_eq!(defaults.available_new, 1, "the new card is available to study");
    }
}
