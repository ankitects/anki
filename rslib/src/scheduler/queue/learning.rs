// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{cmp::Ordering, collections::VecDeque};

use super::{undo::CutoffSnapshot, CardQueues};
use crate::{prelude::*, scheduler::timing::SchedTimingToday};

#[derive(Debug, Clone, Copy, PartialEq, PartialOrd, Eq, Ord)]
pub(crate) struct LearningQueueEntry {
    // due comes first, so the derived ordering sorts by due
    pub due: TimestampSecs,
    pub id: CardId,
    pub mtime: TimestampSecs,
}

impl CardQueues {
    /// Intraday learning cards that can be shown immediately.
    pub(super) fn intraday_now_iter(&self) -> impl Iterator<Item = &LearningQueueEntry> {
        let cutoff = self.current_learning_cutoff;
        self.intraday_learning
            .iter()
            .take_while(move |e| e.due <= cutoff)
    }

    /// Intraday learning cards that can be shown after the main queue is empty.
    pub(super) fn intraday_ahead_iter(&self) -> impl Iterator<Item = &LearningQueueEntry> {
        let cutoff = self.current_learning_cutoff;
        let ahead_cutoff = self.current_learn_ahead_cutoff();
        self.intraday_learning
            .iter()
            .skip_while(move |e| e.due <= cutoff)
            .take_while(move |e| e.due <= ahead_cutoff)
    }

    /// Increase the cutoff to the current time, and increase the learning count
    /// for any new cards that now fall within the cutoff.
    pub(super) fn update_learning_cutoff_and_count(&mut self) -> Box<CutoffSnapshot> {
        let change = CutoffSnapshot {
            learning_count: self.counts.learning,
            learning_cutoff: self.current_learning_cutoff,
        };
        self.current_learning_cutoff = TimestampSecs::now();
        let ahead_cutoff = self.current_learn_ahead_cutoff();
        let new_learning_cards = self
            .intraday_learning
            .iter()
            .skip(self.counts.learning)
            .take_while(|e| e.due <= ahead_cutoff)
            .count();
        self.counts.learning += new_learning_cards;

        Box::new(change)
    }

    /// Given the just-answered `card`, place it back in the learning queues if it's still
    /// due today. Avoid placing it in a position where it would be shown again immediately.
    pub(super) fn maybe_requeue_learning_card(
        &mut self,
        card: &Card,
        timing: SchedTimingToday,
    ) -> Option<LearningQueueEntry> {
        // not due today?
        if !card.is_intraday_learning() || card.due >= timing.next_day_at.0 as i32 {
            return None;
        }

        let entry = LearningQueueEntry {
            due: TimestampSecs(card.due as i64),
            id: card.id,
            mtime: card.mtime,
        };

        Some(self.requeue_learning_entry(entry))
    }

    pub(super) fn cutoff_snapshot(&self) -> Box<CutoffSnapshot> {
        Box::new(CutoffSnapshot {
            learning_count: self.counts.learning,
            learning_cutoff: self.current_learning_cutoff,
        })
    }

    pub(super) fn restore_cutoff(&mut self, change: &CutoffSnapshot) -> Box<CutoffSnapshot> {
        let current = self.cutoff_snapshot();
        self.counts.learning = change.learning_count;
        self.current_learning_cutoff = change.learning_cutoff;
        current
    }

    /// Caller must have validated learning entry is due today.
    pub(super) fn requeue_learning_entry(
        &mut self,
        mut entry: LearningQueueEntry,
    ) -> LearningQueueEntry {
        let cutoff = self.current_learn_ahead_cutoff();
        if entry.due <= cutoff && self.learning_collapsed() {
            if let Some(next) = self.intraday_learning.front() {
                // ensure the card is scheduled after the next due card
                if next.due >= entry.due {
                    if next.due < cutoff {
                        entry.due = next.due.adding_secs(1)
                    } else {
                        // or outside the cutoff, in cases where the next due
                        // card is due later than the cutoff
                        entry.due = cutoff.adding_secs(60);
                    }
                }
            } else {
                // nothing else waiting to review; make this due a minute after
                // cutoff
                entry.due = cutoff.adding_secs(60);
            }
        }

        self.insert_intraday_learning_card(entry);

        entry
    }

    fn learning_collapsed(&self) -> bool {
        self.main.is_empty()
    }

    /// Remove the head of the intraday learning queue, and update counts.
    pub(super) fn pop_intraday_learning(&mut self) -> Option<LearningQueueEntry> {
        self.intraday_learning.pop_front().map(|head| {
            // FIXME:
            // under normal circumstances this should not go below 0, but currently
            // the Python unit tests answer learning cards before they're due
            self.counts.learning = self.counts.learning.saturating_sub(1);
            head
        })
    }

    /// Add an undone entry to the top of the intraday learning queue.
    pub(super) fn push_intraday_learning(&mut self, entry: LearningQueueEntry) {
        self.intraday_learning.push_front(entry);
        self.counts.learning += 1;
    }

    /// Adds an intraday learning card to the correct position of the queue, and
    /// increments learning count if card is due.
    pub(super) fn insert_intraday_learning_card(&mut self, entry: LearningQueueEntry) {
        if entry.due <= self.current_learn_ahead_cutoff() {
            self.counts.learning += 1;
        }

        let target_idx = binary_search_by(&self.intraday_learning, |e| e.due.cmp(&entry.due))
            .unwrap_or_else(|e| e);
        self.intraday_learning.insert(target_idx, entry);
    }

    /// Remove an inserted intraday learning card after a lapse is undone, adjusting
    /// counts.
    pub(super) fn remove_intraday_learning_card(
        &mut self,
        card_id: CardId,
    ) -> Option<LearningQueueEntry> {
        if let Some(position) = self.intraday_learning.iter().position(|e| e.id == card_id) {
            let entry = self.intraday_learning.remove(position).unwrap();
            if entry.due
                <= self
                    .current_learning_cutoff
                    .adding_secs(self.learn_ahead_secs)
            {
                // The saturating_sub() deals with a corner case: one remaining
                // learning card, delayed so it doesn't appear twice. Will be
                // within the current cutoff but not included in the count.
                self.counts.learning = self.counts.learning.saturating_sub(1);
            }
            Some(entry)
        } else {
            None
        }
    }

    fn current_learn_ahead_cutoff(&self) -> TimestampSecs {
        self.current_learning_cutoff
            .adding_secs(self.learn_ahead_secs)
    }
}

/// Adapted from the Rust stdlib VecDeque implementation; we can drop this when the following
/// lands: https://github.com/rust-lang/rust/issues/78021
fn binary_search_by<'a, F, T>(deque: &'a VecDeque<T>, mut f: F) -> Result<usize, usize>
where
    F: FnMut(&'a T) -> Ordering,
{
    let (front, back) = deque.as_slices();

    match back.first().map(|elem| f(elem)) {
        Some(Ordering::Less) | Some(Ordering::Equal) => back
            .binary_search_by(f)
            .map(|idx| idx + front.len())
            .map_err(|idx| idx + front.len()),
        _ => front.binary_search_by(f),
    }
}
