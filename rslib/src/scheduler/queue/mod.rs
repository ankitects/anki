// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod builder;
mod entry;
mod learning;
mod limits;
mod main;
pub(crate) mod undo;

use std::{
    cmp::Reverse,
    collections::{BinaryHeap, VecDeque},
};

use crate::{backend_proto as pb, prelude::*, timestamp::TimestampSecs};
pub(crate) use builder::{DueCard, NewCard};
pub(crate) use {
    entry::{QueueEntry, QueueEntryKind},
    learning::LearningQueueEntry,
    main::{MainQueueEntry, MainQueueEntryKind},
};

use self::undo::QueueUpdate;

use super::{states::NextCardStates, timing::SchedTimingToday};

#[derive(Debug)]
pub(crate) struct CardQueues {
    counts: Counts,

    /// Any undone items take precedence.
    undo: Vec<QueueEntry>,

    main: VecDeque<MainQueueEntry>,

    due_learning: VecDeque<LearningQueueEntry>,

    later_learning: BinaryHeap<Reverse<LearningQueueEntry>>,

    selected_deck: DeckID,
    current_day: u32,
    learn_ahead_secs: i64,
}

#[derive(Debug, Copy, Clone)]
pub(crate) struct Counts {
    pub new: usize,
    pub learning: usize,
    pub review: usize,
}

#[derive(Debug)]
pub(crate) struct QueuedCard {
    pub card: Card,
    pub kind: QueueEntryKind,
    pub next_states: NextCardStates,
}

pub(crate) struct QueuedCards {
    pub cards: Vec<QueuedCard>,
    pub new_count: usize,
    pub learning_count: usize,
    pub review_count: usize,
}

impl CardQueues {
    /// Get the next due card, if there is one.
    fn next_entry(&mut self, now: TimestampSecs) -> Option<QueueEntry> {
        self.next_undo_entry()
            .map(Into::into)
            .or_else(|| self.next_learning_entry_due_before_now(now).map(Into::into))
            .or_else(|| self.next_main_entry().map(Into::into))
            .or_else(|| self.next_learning_entry_learning_ahead().map(Into::into))
    }

    /// Remove the provided card from the top of the queues.
    /// If it was not at the top, return an error.
    fn pop_answered(&mut self, id: CardID) -> Result<QueueEntry> {
        if let Some(entry) = self.pop_undo_entry(id) {
            Ok(entry)
        } else if let Some(entry) = self.pop_main_entry(id) {
            Ok(entry.into())
        } else if let Some(entry) = self.pop_learning_entry(id) {
            Ok(entry.into())
        } else {
            Err(AnkiError::invalid_input("not at top of queue"))
        }
    }

    pub(crate) fn counts(&self) -> Counts {
        self.counts
    }

    fn is_stale(&self, deck: DeckID, current_day: u32) -> bool {
        self.selected_deck != deck || self.current_day != current_day
    }

    fn update_after_answering_card(
        &mut self,
        card: &Card,
        timing: SchedTimingToday,
    ) -> Result<Box<QueueUpdate>> {
        let entry = self.pop_answered(card.id)?;
        let requeued_learning = self.maybe_requeue_learning_card(card, timing);

        Ok(Box::new(QueueUpdate {
            entry,
            learning_requeue: requeued_learning,
        }))
    }
}

impl Collection {
    pub(crate) fn get_queued_cards(
        &mut self,
        fetch_limit: u32,
        intraday_learning_only: bool,
    ) -> Result<pb::GetQueuedCardsOut> {
        if let Some(next_cards) = self.next_cards(fetch_limit, intraday_learning_only)? {
            Ok(pb::GetQueuedCardsOut {
                value: Some(pb::get_queued_cards_out::Value::QueuedCards(
                    next_cards.into(),
                )),
            })
        } else {
            Ok(pb::GetQueuedCardsOut {
                value: Some(pb::get_queued_cards_out::Value::CongratsInfo(
                    self.congrats_info()?,
                )),
            })
        }
    }

    /// This is automatically done when transact() is called for everything
    /// except card answers, so unless you are modifying state outside of a
    /// transaction, you probably don't need this.
    pub(crate) fn clear_study_queues(&mut self) {
        self.state.card_queues = None;
    }

    pub(crate) fn update_queues_after_answering_card(
        &mut self,
        card: &Card,
        timing: SchedTimingToday,
    ) -> Result<()> {
        if let Some(queues) = &mut self.state.card_queues {
            let mutation = queues.update_after_answering_card(card, timing)?;
            self.save_queue_update_undo(mutation);
            Ok(())
        } else {
            // we currenly allow the queues to be empty for unit tests
            Ok(())
        }
    }

    pub(crate) fn get_queues(&mut self) -> Result<&mut CardQueues> {
        let timing = self.timing_today()?;
        let deck = self.get_current_deck_id();
        let need_rebuild = self
            .state
            .card_queues
            .as_ref()
            .map(|q| q.is_stale(deck, timing.days_elapsed))
            .unwrap_or(true);
        if need_rebuild {
            self.state.card_queues = Some(self.build_queues(deck)?);
        }

        Ok(self.state.card_queues.as_mut().unwrap())
    }

    fn next_cards(
        &mut self,
        _fetch_limit: u32,
        _intraday_learning_only: bool,
    ) -> Result<Option<QueuedCards>> {
        let queues = self.get_queues()?;
        let mut cards = vec![];
        if let Some(entry) = queues.next_entry(TimestampSecs::now()) {
            let card = self
                .storage
                .get_card(entry.card_id())?
                .ok_or(AnkiError::NotFound)?;
            if card.mtime != entry.mtime() {
                return Err(AnkiError::invalid_input(
                    "bug: card modified without updating queue",
                ));
            }

            // fixme: pass in card instead of id
            let next_states = self.get_next_card_states(card.id)?;

            cards.push(QueuedCard {
                card,
                next_states,
                kind: entry.kind(),
            });
        }

        if cards.is_empty() {
            Ok(None)
        } else {
            let counts = self.get_queues()?.counts();
            Ok(Some(QueuedCards {
                cards,
                new_count: counts.new,
                learning_count: counts.learning,
                review_count: counts.review,
            }))
        }
    }

    #[cfg(test)]
    pub(crate) fn next_card(&mut self) -> Result<Option<QueuedCard>> {
        Ok(self
            .next_cards(1, false)?
            .map(|mut resp| resp.cards.pop().unwrap()))
    }
}
