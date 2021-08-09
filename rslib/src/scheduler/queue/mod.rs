// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod builder;
mod entry;
mod learning;
mod main;
pub(crate) mod undo;

use std::collections::VecDeque;

pub(crate) use builder::{DueCard, NewCard};
pub(crate) use entry::{QueueEntry, QueueEntryKind};
pub(crate) use learning::LearningQueueEntry;
pub(crate) use main::{MainQueueEntry, MainQueueEntryKind};

use self::undo::QueueUpdate;
use super::{states::NextCardStates, timing::SchedTimingToday};
use crate::{prelude::*, timestamp::TimestampSecs};

#[derive(Debug)]
pub(crate) struct CardQueues {
    counts: Counts,
    main: VecDeque<MainQueueEntry>,
    intraday_learning: VecDeque<LearningQueueEntry>,
    selected_deck: DeckId,
    current_day: u32,
    learn_ahead_secs: i64,
    /// Updated each time a card is answered, and by get_queued_cards() when the
    /// counts are zero. Ensures we don't show a newly-due learning card after a
    /// user returns from editing a review card.
    current_learning_cutoff: TimestampSecs,
}

#[derive(Debug, Copy, Clone)]
pub struct Counts {
    pub new: usize,
    pub learning: usize,
    pub review: usize,
}

impl Counts {
    fn all_zero(self) -> bool {
        self.new == 0 && self.learning == 0 && self.review == 0
    }
}

#[derive(Debug, Clone)]
pub struct QueuedCard {
    pub card: Card,
    pub kind: QueueEntryKind,
    pub next_states: NextCardStates,
}

#[derive(Debug)]
pub struct QueuedCards {
    pub cards: Vec<QueuedCard>,
    pub new_count: usize,
    pub learning_count: usize,
    pub review_count: usize,
}

impl Collection {
    pub fn get_next_card(&mut self) -> Result<Option<QueuedCard>> {
        self.get_queued_cards(1, false)
            .map(|queued| queued.cards.get(0).cloned())
    }

    pub fn get_queued_cards(
        &mut self,
        fetch_limit: usize,
        intraday_learning_only: bool,
    ) -> Result<QueuedCards> {
        let queues = self.get_queues()?;
        let counts = queues.counts();
        let entries: Vec<_> = if intraday_learning_only {
            queues
                .intraday_now_iter()
                .chain(queues.intraday_ahead_iter())
                .map(Into::into)
                .collect()
        } else {
            queues.iter().take(fetch_limit).collect()
        };
        let cards: Vec<_> = entries
            .into_iter()
            .map(|entry| {
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

                Ok(QueuedCard {
                    card,
                    next_states,
                    kind: entry.kind(),
                })
            })
            .collect::<Result<_>>()?;
        Ok(QueuedCards {
            cards,
            new_count: counts.new,
            learning_count: counts.learning,
            review_count: counts.review,
        })
    }
}

impl CardQueues {
    /// An iterator over the card queues, in the order the cards will
    /// be presented.
    fn iter(&self) -> impl Iterator<Item = QueueEntry> + '_ {
        self.intraday_now_iter()
            .map(Into::into)
            .chain(self.main.iter().map(Into::into))
            .chain(self.intraday_ahead_iter().map(Into::into))
    }

    /// Remove the provided card from the top of the queues and
    /// adjust the counts. If it was not at the top, return an error.
    fn pop_entry(&mut self, id: CardId) -> Result<QueueEntry> {
        // This ignores the current cutoff, so may match if the provided
        // learning card is not yet due. It should not happen in normal
        // practice, but does happen in the Python unit tests, as they answer
        // learning cards early.
        if self
            .intraday_learning
            .front()
            .filter(|e| e.id == id)
            .is_some()
        {
            Ok(self.pop_intraday_learning().unwrap().into())
        } else if self.main.front().filter(|e| e.id == id).is_some() {
            Ok(self.pop_main().unwrap().into())
        } else {
            Err(AnkiError::invalid_input("not at top of queue"))
        }
    }

    fn push_undo_entry(&mut self, entry: QueueEntry) {
        match entry {
            QueueEntry::IntradayLearning(entry) => self.push_intraday_learning(entry),
            QueueEntry::Main(entry) => self.push_main(entry),
        }
    }

    /// Return the current due counts. If there are no due cards, the learning
    /// cutoff is updated to the current time first, and any newly-due learning
    /// cards are added to the counts.
    pub(crate) fn counts(&mut self) -> Counts {
        if self.counts.all_zero() {
            // we discard the returned undo information in this case
            self.update_learning_cutoff_and_count();
        }
        self.counts
    }

    fn is_stale(&self, current_day: u32) -> bool {
        self.current_day != current_day
    }
}

impl Collection {
    /// This is automatically done when transact() is called for everything
    /// except card answers, so unless you are modifying state outside of a
    /// transaction, you probably don't need this.
    pub(crate) fn clear_study_queues(&mut self) {
        self.state.card_queues = None;
    }

    pub(crate) fn maybe_clear_study_queues_after_op(&mut self, op: &OpChanges) {
        if op.op != Op::AnswerCard && op.requires_study_queue_rebuild() {
            self.state.card_queues = None;
        }
    }

    pub(crate) fn update_queues_after_answering_card(
        &mut self,
        card: &Card,
        timing: SchedTimingToday,
    ) -> Result<()> {
        if let Some(queues) = &mut self.state.card_queues {
            let entry = queues.pop_entry(card.id)?;
            let requeued_learning = queues.maybe_requeue_learning_card(card, timing);
            let cutoff_change = queues.update_learning_cutoff_and_count();
            self.save_queue_update_undo(Box::new(QueueUpdate {
                entry,
                learning_requeue: requeued_learning,
            }));
            self.save_cutoff_change(cutoff_change);
        } else {
            // we currenly allow the queues to be empty for unit tests
        }

        Ok(())
    }

    pub(crate) fn get_queues(&mut self) -> Result<&mut CardQueues> {
        let timing = self.timing_today()?;
        let deck = self.get_current_deck()?;
        let day_rolled_over = self
            .state
            .card_queues
            .as_ref()
            .map(|q| q.is_stale(timing.days_elapsed))
            .unwrap_or(false);
        if day_rolled_over {
            self.discard_undo_and_study_queues();
        }
        if self.state.card_queues.is_none() {
            self.state.card_queues = Some(self.build_queues(deck.id)?);
        }

        Ok(self.state.card_queues.as_mut().unwrap())
    }
}

// test helpers
#[cfg(test)]
impl Collection {
    pub(crate) fn counts(&mut self) -> [usize; 3] {
        self.get_queued_cards(1, false)
            .map(|q| [q.new_count, q.learning_count, q.review_count])
            .unwrap_or([0; 3])
    }
}
