// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{cmp::Ordering, collections::VecDeque};

use super::CardQueues;
use crate::{prelude::*, scheduler::timing::SchedTimingToday};

#[derive(Debug, Clone, Copy, PartialEq, PartialOrd, Eq, Ord)]
pub(crate) struct LearningQueueEntry {
    // due comes first, so the derived ordering sorts by due
    pub due: TimestampSecs,
    pub id: CardId,
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
        self.learning.front().copied()
    }

    pub(super) fn pop_learning_entry(&mut self, id: CardId) -> Option<LearningQueueEntry> {
        if let Some(top) = self.learning.front() {
            if top.id == id {
                // under normal circumstances this should not go below 0, but currently
                // the Python unit tests answer learning cards before they're due
                self.counts.learning = self.counts.learning.saturating_sub(1);
                return self.learning.pop_front();
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
        if !card.is_intraday_learning() || card.due >= timing.next_day_at.0 as i32 {
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
        let cutoff = timing.now.adding_secs(self.learn_ahead_secs);
        if entry.due <= cutoff && self.learning_collapsed() {
            if let Some(next) = self.learning.front() {
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

        self.push_learning_card(entry);

        entry
    }

    fn learning_collapsed(&self) -> bool {
        self.main.is_empty()
    }

    /// Adds card, maintaining correct sort order, and increments learning count if
    /// card is due within cutoff.
    pub(super) fn push_learning_card(&mut self, entry: LearningQueueEntry) {
        let target_idx =
            binary_search_by(&self.learning, |e| e.due.cmp(&entry.due)).unwrap_or_else(|e| e);
        if target_idx < self.counts.learning {
            self.counts.learning += 1;
        }
        self.learning.insert(target_idx, entry);
    }

    fn check_for_newly_due_learning_cards(&mut self, cutoff: TimestampSecs) {
        self.counts.learning += self
            .learning
            .iter()
            .skip(self.counts.learning)
            .take_while(|e| e.due <= cutoff)
            .count();
    }

    pub(super) fn remove_requeued_learning_card_after_undo(&mut self, id: CardId) {
        let due_idx = self.learning.iter().enumerate().find_map(|(idx, entry)| {
            if entry.id == id {
                Some(idx)
            } else {
                None
            }
        });
        if let Some(idx) = due_idx {
            // FIXME: if we remove the saturating_sub from pop_learning(), maybe
            // this can go too?
            self.counts.learning = self.counts.learning.saturating_sub(1);
            self.learning.remove(idx);
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
