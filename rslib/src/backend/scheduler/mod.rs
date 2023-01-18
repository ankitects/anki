// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod answering;
mod states;

use super::Backend;
use crate::pb;
pub(super) use crate::pb::scheduler::scheduler_service::Service as SchedulerService;
use crate::prelude::*;
use crate::scheduler::new::NewCardDueOrder;
use crate::scheduler::states::CardState;
use crate::scheduler::states::SchedulingStates;
use crate::stats::studied_today;

impl SchedulerService for Backend {
    /// This behaves like _updateCutoff() in older code - it also unburies at
    /// the start of a new day.
    fn sched_timing_today(
        &self,
        _input: pb::generic::Empty,
    ) -> Result<pb::scheduler::SchedTimingTodayResponse> {
        self.with_col(|col| {
            let timing = col.timing_today()?;
            col.unbury_if_day_rolled_over(timing)?;
            Ok(timing.into())
        })
    }

    /// Fetch data from DB and return rendered string.
    fn studied_today(&self, _input: pb::generic::Empty) -> Result<pb::generic::String> {
        self.with_col(|col| col.studied_today().map(Into::into))
    }

    /// Message rendering only, for old graphs.
    fn studied_today_message(
        &self,
        input: pb::scheduler::StudiedTodayMessageRequest,
    ) -> Result<pb::generic::String> {
        Ok(studied_today(input.cards, input.seconds as f32, &self.tr).into())
    }

    fn update_stats(&self, input: pb::scheduler::UpdateStatsRequest) -> Result<pb::generic::Empty> {
        self.with_col(|col| {
            col.transact_no_undo(|col| {
                let today = col.current_due_day(0)?;
                let usn = col.usn()?;
                col.update_deck_stats(today, usn, input).map(Into::into)
            })
        })
    }

    fn extend_limits(
        &self,
        input: pb::scheduler::ExtendLimitsRequest,
    ) -> Result<pb::generic::Empty> {
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
        input: pb::decks::DeckId,
    ) -> Result<pb::scheduler::CountsForDeckTodayResponse> {
        self.with_col(|col| col.counts_for_deck_today(input.did.into()))
    }

    fn congrats_info(
        &self,
        _input: pb::generic::Empty,
    ) -> Result<pb::scheduler::CongratsInfoResponse> {
        self.with_col(|col| col.congrats_info())
    }

    fn restore_buried_and_suspended_cards(
        &self,
        input: pb::cards::CardIds,
    ) -> Result<pb::collection::OpChanges> {
        let cids: Vec<_> = input.into();
        self.with_col(|col| col.unbury_or_unsuspend_cards(&cids).map(Into::into))
    }

    fn unbury_deck(
        &self,
        input: pb::scheduler::UnburyDeckRequest,
    ) -> Result<pb::collection::OpChanges> {
        self.with_col(|col| {
            col.unbury_deck(input.deck_id.into(), input.mode())
                .map(Into::into)
        })
    }

    fn bury_or_suspend_cards(
        &self,
        input: pb::scheduler::BuryOrSuspendCardsRequest,
    ) -> Result<pb::collection::OpChangesWithCount> {
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

    fn empty_filtered_deck(&self, input: pb::decks::DeckId) -> Result<pb::collection::OpChanges> {
        self.with_col(|col| col.empty_filtered_deck(input.did.into()).map(Into::into))
    }

    fn rebuild_filtered_deck(
        &self,
        input: pb::decks::DeckId,
    ) -> Result<pb::collection::OpChangesWithCount> {
        self.with_col(|col| col.rebuild_filtered_deck(input.did.into()).map(Into::into))
    }

    fn schedule_cards_as_new(
        &self,
        input: pb::scheduler::ScheduleCardsAsNewRequest,
    ) -> Result<pb::collection::OpChanges> {
        self.with_col(|col| {
            let cids = input.card_ids.into_newtype(CardId);
            col.reschedule_cards_as_new(
                &cids,
                input.log,
                input.restore_position,
                input.reset_counts,
                input
                    .context
                    .and_then(pb::scheduler::schedule_cards_as_new_request::Context::from_i32),
            )
            .map(Into::into)
        })
    }

    fn schedule_cards_as_new_defaults(
        &self,
        input: pb::scheduler::ScheduleCardsAsNewDefaultsRequest,
    ) -> Result<pb::scheduler::ScheduleCardsAsNewDefaultsResponse> {
        self.with_col(|col| Ok(col.reschedule_cards_as_new_defaults(input.context())))
    }

    fn set_due_date(
        &self,
        input: pb::scheduler::SetDueDateRequest,
    ) -> Result<pb::collection::OpChanges> {
        let config = input.config_key.map(|v| v.key().into());
        let days = input.days;
        let cids = input.card_ids.into_newtype(CardId);
        self.with_col(|col| col.set_due_date(&cids, &days, config).map(Into::into))
    }

    fn sort_cards(
        &self,
        input: pb::scheduler::SortCardsRequest,
    ) -> Result<pb::collection::OpChangesWithCount> {
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

    fn reposition_defaults(
        &self,
        _input: pb::generic::Empty,
    ) -> Result<pb::scheduler::RepositionDefaultsResponse> {
        self.with_col(|col| Ok(col.reposition_defaults()))
    }

    fn sort_deck(
        &self,
        input: pb::scheduler::SortDeckRequest,
    ) -> Result<pb::collection::OpChangesWithCount> {
        self.with_col(|col| {
            col.sort_deck_legacy(input.deck_id.into(), input.randomize)
                .map(Into::into)
        })
    }

    fn get_scheduling_states(
        &self,
        input: pb::cards::CardId,
    ) -> Result<pb::scheduler::SchedulingStates> {
        let cid: CardId = input.into();
        self.with_col(|col| col.get_scheduling_states(cid))
            .map(Into::into)
    }

    fn describe_next_states(
        &self,
        input: pb::scheduler::SchedulingStates,
    ) -> Result<pb::generic::StringList> {
        let states: SchedulingStates = input.into();
        self.with_col(|col| col.describe_next_states(states))
            .map(Into::into)
    }

    fn state_is_leech(&self, input: pb::scheduler::SchedulingState) -> Result<pb::generic::Bool> {
        let state: CardState = input.into();
        Ok(state.leeched().into())
    }

    fn answer_card(&self, input: pb::scheduler::CardAnswer) -> Result<pb::collection::OpChanges> {
        self.with_col(|col| col.answer_card(&mut input.into()))
            .map(Into::into)
    }

    fn upgrade_scheduler(&self, _input: pb::generic::Empty) -> Result<pb::generic::Empty> {
        self.with_col(|col| col.transact_no_undo(|col| col.upgrade_to_v2_scheduler()))
            .map(Into::into)
    }

    fn get_queued_cards(
        &self,
        input: pb::scheduler::GetQueuedCardsRequest,
    ) -> Result<pb::scheduler::QueuedCards> {
        self.with_col(|col| {
            col.get_queued_cards(input.fetch_limit as usize, input.intraday_learning_only)
                .map(Into::into)
        })
    }

    fn custom_study(
        &self,
        input: pb::scheduler::CustomStudyRequest,
    ) -> Result<pb::collection::OpChanges> {
        self.with_col(|col| col.custom_study(input)).map(Into::into)
    }

    fn custom_study_defaults(
        &self,
        input: pb::scheduler::CustomStudyDefaultsRequest,
    ) -> Result<pb::scheduler::CustomStudyDefaultsResponse> {
        self.with_col(|col| col.custom_study_defaults(input.deck_id.into()))
    }
}

impl From<crate::scheduler::timing::SchedTimingToday> for pb::scheduler::SchedTimingTodayResponse {
    fn from(
        t: crate::scheduler::timing::SchedTimingToday,
    ) -> pb::scheduler::SchedTimingTodayResponse {
        pb::scheduler::SchedTimingTodayResponse {
            days_elapsed: t.days_elapsed,
            next_day_at: t.next_day_at.0,
        }
    }
}
