// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod answering;
mod states;

use anki_proto::generic;
use anki_proto::scheduler;
pub(super) use anki_proto::scheduler::scheduler_service::Service as SchedulerService;
use anki_proto::scheduler::SchedulingStatesWithContext;
use anki_proto::scheduler::SetSchedulingStatesRequest;

use super::Backend;
use crate::prelude::*;
use crate::scheduler::new::NewCardDueOrder;
use crate::scheduler::states::CardState;
use crate::scheduler::states::SchedulingStates;
use crate::stats::studied_today;

impl SchedulerService for Backend {
    type Error = AnkiError;

    /// This behaves like _updateCutoff() in older code - it also unburies at
    /// the start of a new day.
    fn sched_timing_today(&self) -> Result<scheduler::SchedTimingTodayResponse> {
        self.with_col(|col| {
            let timing = col.timing_today()?;
            col.unbury_if_day_rolled_over(timing)?;
            Ok(timing.into())
        })
    }

    /// Fetch data from DB and return rendered string.
    fn studied_today(&self) -> Result<generic::String> {
        self.with_col(|col| col.studied_today().map(Into::into))
    }

    /// Message rendering only, for old graphs.
    fn studied_today_message(
        &self,
        input: scheduler::StudiedTodayMessageRequest,
    ) -> Result<generic::String> {
        Ok(studied_today(input.cards, input.seconds as f32, &self.tr).into())
    }

    fn update_stats(&self, input: scheduler::UpdateStatsRequest) -> Result<()> {
        self.with_col(|col| {
            col.transact_no_undo(|col| {
                let today = col.current_due_day(0)?;
                let usn = col.usn()?;
                col.update_deck_stats(today, usn, input).map(Into::into)
            })
        })
    }

    fn extend_limits(&self, input: scheduler::ExtendLimitsRequest) -> Result<()> {
        self.with_col(|col| {
            col.transact_no_undo(|col| {
                let today = col.current_due_day(0)?;
                let usn = col.usn()?;
                col.extend_limits(
                    today,
                    usn,
                    input.deck_id.into(),
                    input.new_delta,
                    input.review_delta,
                )
                .map(Into::into)
            })
        })
    }

    fn counts_for_deck_today(
        &self,
        input: anki_proto::decks::DeckId,
    ) -> Result<scheduler::CountsForDeckTodayResponse> {
        self.with_col(|col| col.counts_for_deck_today(input.did.into()))
    }

    fn congrats_info(&self) -> Result<scheduler::CongratsInfoResponse> {
        self.with_col(|col| col.congrats_info())
    }

    fn restore_buried_and_suspended_cards(
        &self,
        input: anki_proto::cards::CardIds,
    ) -> Result<anki_proto::collection::OpChanges> {
        let cids: Vec<_> = input.cids.into_iter().map(CardId).collect();
        self.with_col(|col| col.unbury_or_unsuspend_cards(&cids).map(Into::into))
    }

    fn unbury_deck(
        &self,
        input: scheduler::UnburyDeckRequest,
    ) -> Result<anki_proto::collection::OpChanges> {
        self.with_col(|col| {
            col.unbury_deck(input.deck_id.into(), input.mode())
                .map(Into::into)
        })
    }

    fn bury_or_suspend_cards(
        &self,
        input: scheduler::BuryOrSuspendCardsRequest,
    ) -> Result<anki_proto::collection::OpChangesWithCount> {
        self.with_col(|col| {
            let mode = input.mode();
            let cids = if input.card_ids.is_empty() {
                col.storage
                    .card_ids_of_notes(&input.note_ids.into_newtype(NoteId))?
            } else {
                input.card_ids.into_newtype(CardId)
            };
            col.bury_or_suspend_cards(&cids, mode).map(Into::into)
        })
    }

    fn empty_filtered_deck(
        &self,
        input: anki_proto::decks::DeckId,
    ) -> Result<anki_proto::collection::OpChanges> {
        self.with_col(|col| col.empty_filtered_deck(input.did.into()).map(Into::into))
    }

    fn rebuild_filtered_deck(
        &self,
        input: anki_proto::decks::DeckId,
    ) -> Result<anki_proto::collection::OpChangesWithCount> {
        self.with_col(|col| col.rebuild_filtered_deck(input.did.into()).map(Into::into))
    }

    fn schedule_cards_as_new(
        &self,
        input: scheduler::ScheduleCardsAsNewRequest,
    ) -> Result<anki_proto::collection::OpChanges> {
        self.with_col(|col| {
            let cids = input.card_ids.into_newtype(CardId);
            col.reschedule_cards_as_new(
                &cids,
                input.log,
                input.restore_position,
                input.reset_counts,
                input
                    .context
                    .and_then(scheduler::schedule_cards_as_new_request::Context::from_i32),
            )
            .map(Into::into)
        })
    }

    fn schedule_cards_as_new_defaults(
        &self,
        input: scheduler::ScheduleCardsAsNewDefaultsRequest,
    ) -> Result<scheduler::ScheduleCardsAsNewDefaultsResponse> {
        self.with_col(|col| Ok(col.reschedule_cards_as_new_defaults(input.context())))
    }

    fn set_due_date(
        &self,
        input: scheduler::SetDueDateRequest,
    ) -> Result<anki_proto::collection::OpChanges> {
        let config = input.config_key.map(|v| v.key().into());
        let days = input.days;
        let cids = input.card_ids.into_newtype(CardId);
        self.with_col(|col| col.set_due_date(&cids, &days, config).map(Into::into))
    }

    fn sort_cards(
        &self,
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
        self.with_col(|col| {
            col.sort_cards(&cids, start, step, order, shift)
                .map(Into::into)
        })
    }

    fn reposition_defaults(&self) -> Result<scheduler::RepositionDefaultsResponse> {
        self.with_col(|col| Ok(col.reposition_defaults()))
    }

    fn sort_deck(
        &self,
        input: scheduler::SortDeckRequest,
    ) -> Result<anki_proto::collection::OpChangesWithCount> {
        self.with_col(|col| {
            col.sort_deck_legacy(input.deck_id.into(), input.randomize)
                .map(Into::into)
        })
    }

    fn get_scheduling_states(
        &self,
        input: anki_proto::cards::CardId,
    ) -> Result<scheduler::SchedulingStates> {
        let cid: CardId = input.into();
        self.with_col(|col| col.get_scheduling_states(cid))
            .map(Into::into)
    }

    fn describe_next_states(
        &self,
        input: scheduler::SchedulingStates,
    ) -> Result<generic::StringList> {
        let states: SchedulingStates = input.into();
        self.with_col(|col| col.describe_next_states(states))
            .map(Into::into)
    }

    fn state_is_leech(&self, input: scheduler::SchedulingState) -> Result<generic::Bool> {
        let state: CardState = input.into();
        Ok(state.leeched().into())
    }

    fn answer_card(
        &self,
        input: scheduler::CardAnswer,
    ) -> Result<anki_proto::collection::OpChanges> {
        self.with_col(|col| col.answer_card(&mut input.into()))
            .map(Into::into)
    }

    fn upgrade_scheduler(&self) -> Result<()> {
        self.with_col(|col| col.transact_no_undo(|col| col.upgrade_to_v2_scheduler()))
            .map(Into::into)
    }

    fn get_queued_cards(
        &self,
        input: scheduler::GetQueuedCardsRequest,
    ) -> Result<scheduler::QueuedCards> {
        self.with_col(|col| {
            col.get_queued_cards(input.fetch_limit as usize, input.intraday_learning_only)
                .map(Into::into)
        })
    }

    fn custom_study(
        &self,
        input: scheduler::CustomStudyRequest,
    ) -> Result<anki_proto::collection::OpChanges> {
        self.with_col(|col| col.custom_study(input)).map(Into::into)
    }

    fn custom_study_defaults(
        &self,
        input: scheduler::CustomStudyDefaultsRequest,
    ) -> Result<scheduler::CustomStudyDefaultsResponse> {
        self.with_col(|col| col.custom_study_defaults(input.deck_id.into()))
    }

    fn get_scheduling_states_with_context(&self) -> Result<SchedulingStatesWithContext> {
        invalid_input!("the frontend should implement this")
    }

    fn set_scheduling_states(&self, _input: SetSchedulingStatesRequest) -> Result<()> {
        invalid_input!("the frontend should implement this")
    }
}
