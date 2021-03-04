// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    cmp::{Ordering, Reverse},
    collections::VecDeque,
};

use super::CardQueues;
use crate::{prelude::*, scheduler::timing::SchedTimingToday};

#[derive(Debug, Clone, Copy, PartialEq, PartialOrd, Eq, Ord)]
pub(crate) struct LearningQueueEntry {
    // due comes first, so the derived ordering sorts by due
    pub due: TimestampSecs,
    pub id: CardID,
    pub mtime: TimestampSecs,
}

impl CardQueues {
    /// Check for any newly due cards, and then return the first, if any,
    /// that is due before now.
    pub(super) fn next_learning_entry_due_before_now(
        &mut self,
        now: TimestampSecs,
    ) -> Option<LearningQueueEntry> {
        let learn_ahead_cutoff = now.adding_secs(self.learn_ahead_secs);
        self.check_for_newly_due_learning_cards(learn_ahead_cutoff);
        self.next_learning_entry_learning_ahead()
            .filter(|c| c.due <= now)
    }

    /// Check for due learning cards up to the learn ahead limit.
    /// Does not check for newly due cards, as that is already done by
    /// next_learning_entry_due_before_now()
    pub(super) fn next_learning_entry_learning_ahead(&self) -> Option<LearningQueueEntry> {
        self.due_learning.front().copied()
    }

    pub(super) fn pop_learning_entry(&mut self, id: CardID) -> Option<LearningQueueEntry> {
        if let Some(top) = self.due_learning.front() {
            if top.id == id {
                self.counts.learning -= 1;
                return self.due_learning.pop_front();
            }
        }

        // fixme: remove this in the future
        // the current python unit tests answer learning cards before they're due,
        // so for now we also check the head of the later_due queue
        if let Some(top) = self.later_learning.peek() {
            if top.0.id == id {
                // self.counts.learning -= 1;
                return self.later_learning.pop().map(|c| c.0);
            }
        }

        None
    }

    /// Given the just-answered `card`, place it back in the learning queues if it's still
    /// due today. Avoid placing it in a position where it would be shown again immediately.
    pub(super) fn maybe_requeue_learning_card(
        &mut self,
        card: &Card,
        timing: SchedTimingToday,
    ) -> Option<LearningQueueEntry> {
        // not due today?
        if !card.is_intraday_learning() || card.due >= timing.next_day_at as i32 {
            return None;
        }

        let entry = LearningQueueEntry {
            due: TimestampSecs(card.due as i64),
            id: card.id,
            mtime: card.mtime,
        };

        Some(self.requeue_learning_entry(entry, timing))
    }

    /// Caller must have validated learning entry is due today.
    pub(super) fn requeue_learning_entry(
        &mut self,
        mut entry: LearningQueueEntry,
        timing: SchedTimingToday,
    ) -> LearningQueueEntry {
        let learn_ahead_limit = timing.now.adding_secs(self.learn_ahead_secs);

        if entry.due < learn_ahead_limit {
            if self.learning_collapsed() {
                if let Some(next) = self.due_learning.front() {
                    if next.due >= entry.due {
                        // the earliest due card is due later than this one; make this one
                        // due after that one
                        entry.due = next.due.adding_secs(1);
                    }
                    self.push_due_learning_card(entry);
                } else {
                    // nothing else waiting to review; make this due in a minute
                    entry.due = learn_ahead_limit.adding_secs(60);
                    self.later_learning.push(Reverse(entry));
                }
            } else {
                // not collapsed; can add normally
                self.push_due_learning_card(entry);
            }
        } else {
            // due outside current learn ahead limit, but later today
            self.later_learning.push(Reverse(entry));
        }

        entry
    }

    fn learning_collapsed(&self) -> bool {
        self.main.is_empty()
    }

    /// Adds card, maintaining correct sort order, and increments learning count.
    pub(super) fn push_due_learning_card(&mut self, entry: LearningQueueEntry) {
        self.counts.learning += 1;
        let target_idx =
            binary_search_by(&self.due_learning, |e| e.due.cmp(&entry.due)).unwrap_or_else(|e| e);
        self.due_learning.insert(target_idx, entry);
    }

    fn check_for_newly_due_learning_cards(&mut self, cutoff: TimestampSecs) {
        while let Some(earliest) = self.later_learning.peek() {
            if earliest.0.due > cutoff {
                break;
            }
            let entry = self.later_learning.pop().unwrap().0;
            self.push_due_learning_card(entry);
        }
    }

    pub(super) fn remove_requeued_learning_card_after_undo(&mut self, id: CardID) {
        let due_idx = self
            .due_learning
            .iter()
            .enumerate()
            .find_map(|(idx, entry)| if entry.id == id { Some(idx) } else { None });
        if let Some(idx) = due_idx {
            self.counts.learning -= 1;
            self.due_learning.remove(idx);
        } else {
            // card may be in the later_learning binary heap - we can't remove
            // it in place, so we have to rebuild it
            self.later_learning = self
                .later_learning
                .drain()
                .filter(|e| e.0.id != id)
                .collect();
        }
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
