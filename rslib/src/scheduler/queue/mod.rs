// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod builder;
mod learning;
mod limits;
mod main;

use std::{
    cmp::Reverse,
    collections::{BinaryHeap, VecDeque},
};

use crate::{backend_proto as pb, card::CardQueue, prelude::*, timestamp::TimestampSecs};
pub(crate) use builder::{DueCard, NewCard};

use super::timing::SchedTimingToday;

#[derive(Debug)]
pub(crate) struct CardQueues {
    new_count: usize,
    review_count: usize,
    learn_count: usize,

    main: VecDeque<QueueEntry>,
    due_learning: VecDeque<LearningQueueEntry>,
    later_learning: BinaryHeap<Reverse<LearningQueueEntry>>,

    selected_deck: DeckID,
    current_day: u32,
    learn_ahead_secs: i64,
}

#[derive(Debug)]
pub(crate) struct Counts {
    new: usize,
    learning: usize,
    review: usize,
}

impl CardQueues {
    /// Get the next due card, if there is one.
    fn next_entry(&mut self, now: TimestampSecs) -> Option<QueueEntry> {
        self.next_learning_entry_due_before_now(now)
            .map(Into::into)
            .or_else(|| self.next_main_entry())
            .or_else(|| self.next_learning_entry_learning_ahead().map(Into::into))
    }

    /// Remove the provided card from the top of the learning or main queues.
    /// If it was not at the top, return an error.
    fn pop_answered(&mut self, id: CardID) -> Result<()> {
        if self.pop_main_entry(id).is_none() && self.pop_learning_entry(id).is_none() {
            Err(AnkiError::invalid_input("not at top of queue"))
        } else {
            Ok(())
        }
    }

    fn counts(&self) -> Counts {
        Counts {
            new: self.new_count,
            learning: self.learn_count,
            review: self.review_count,
        }
    }

    fn is_stale(&self, deck: DeckID, current_day: u32) -> bool {
        self.selected_deck != deck || self.current_day != current_day
    }

    fn update_after_answering_card(&mut self, card: &Card, timing: SchedTimingToday) -> Result<()> {
        self.pop_answered(card.id)?;
        self.maybe_requeue_learning_card(card, timing);
        Ok(())
    }

    /// Add a just-undone card back to the appropriate queue, updating counts.
    pub(crate) fn push_undone_card(&mut self, card: &Card) {
        if card.is_intraday_learning() {
            self.push_due_learning_card(LearningQueueEntry {
                due: TimestampSecs(card.due as i64),
                id: card.id,
                mtime: card.mtime,
            })
        } else {
            self.push_main_entry(card.into())
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct QueueEntry {
    id: CardID,
    mtime: TimestampSecs,
    kind: QueueEntryKind,
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) enum QueueEntryKind {
    New,
    /// Includes day-learning cards
    Review,
    Learning,
}

impl PartialOrd for QueueEntry {
    fn partial_cmp(&self, other: &Self) -> Option<std::cmp::Ordering> {
        self.id.partial_cmp(&other.id)
    }
}

impl From<&Card> for QueueEntry {
    fn from(card: &Card) -> Self {
        let kind = match card.queue {
            CardQueue::Learn | CardQueue::PreviewRepeat => QueueEntryKind::Learning,
            CardQueue::New => QueueEntryKind::New,
            CardQueue::Review | CardQueue::DayLearn => QueueEntryKind::Review,
            CardQueue::Suspended | CardQueue::SchedBuried | CardQueue::UserBuried => {
                unreachable!()
            }
        };
        QueueEntry {
            id: card.id,
            mtime: card.mtime,
            kind,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, PartialOrd, Eq, Ord)]
struct LearningQueueEntry {
    // due comes first, so the derived ordering sorts by due
    due: TimestampSecs,
    id: CardID,
    mtime: TimestampSecs,
}

impl From<LearningQueueEntry> for QueueEntry {
    fn from(e: LearningQueueEntry) -> Self {
        Self {
            id: e.id,
            mtime: e.mtime,
            kind: QueueEntryKind::Learning,
        }
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
                value: Some(pb::get_queued_cards_out::Value::QueuedCards(next_cards)),
            })
        } else {
            Ok(pb::GetQueuedCardsOut {
                value: Some(pb::get_queued_cards_out::Value::CongratsInfo(
                    self.congrats_info()?,
                )),
            })
        }
    }

    pub(crate) fn clear_queues(&mut self) {
        self.state.card_queues = None;
    }

    /// FIXME: remove this once undoing is moved into backend
    pub(crate) fn requeue_undone_card(&mut self, card_id: CardID) -> Result<()> {
        let card = self.storage.get_card(card_id)?.ok_or(AnkiError::NotFound)?;
        self.get_queues()?.push_undone_card(&card);
        Ok(())
    }

    pub(crate) fn update_queues_after_answering_card(
        &mut self,
        card: &Card,
        timing: SchedTimingToday,
    ) -> Result<()> {
        if let Some(queues) = &mut self.state.card_queues {
            queues.update_after_answering_card(card, timing)
        } else {
            // we currenly allow the queues to be empty for unit tests
            Ok(())
        }
    }

    fn get_queues(&mut self) -> Result<&mut CardQueues> {
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
    ) -> Result<Option<pb::get_queued_cards_out::QueuedCards>> {
        let queues = self.get_queues()?;
        let mut cards = vec![];
        if let Some(entry) = queues.next_entry(TimestampSecs::now()) {
            let card = self
                .storage
                .get_card(entry.id)?
                .ok_or(AnkiError::NotFound)?;
            if card.mtime != entry.mtime {
                return Err(AnkiError::invalid_input(
                    "bug: card modified without updating queue",
                ));
            }

            // fixme: pass in card instead of id
            let next_states = self.get_next_card_states(card.id)?;

            cards.push(pb::get_queued_cards_out::QueuedCard {
                card: Some(card.into()),
                next_states: Some(next_states.into()),
                queue: match entry.kind {
                    QueueEntryKind::New => 0,
                    QueueEntryKind::Learning => 1,
                    QueueEntryKind::Review => 2,
                },
            });
        }

        if cards.is_empty() {
            Ok(None)
        } else {
            let counts = self.get_queues()?.counts();
            Ok(Some(pb::get_queued_cards_out::QueuedCards {
                cards,
                new_count: counts.new as u32,
                learning_count: counts.learning as u32,
                review_count: counts.review as u32,
            }))
        }
    }
}
