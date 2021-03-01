// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{CardQueues, QueueEntry, QueueEntryKind};
use crate::prelude::*;

impl CardQueues {
    pub(super) fn next_main_entry(&self) -> Option<QueueEntry> {
        self.main.front().copied()
    }

    pub(super) fn pop_main_entry(&mut self, id: CardID) -> Option<QueueEntry> {
        if let Some(last) = self.main.front() {
            if last.id == id {
                match last.kind {
                    QueueEntryKind::New => self.new_count -= 1,
                    QueueEntryKind::Review => self.review_count -= 1,
                    QueueEntryKind::Learning => unreachable!(),
                }
                return self.main.pop_front();
            }
        }

        None
    }

    /// Add an undone card back to the 'front' of the list, and update
    /// the counts.
    pub(super) fn push_main_entry(&mut self, entry: QueueEntry) {
        match entry.kind {
            QueueEntryKind::New => self.new_count += 1,
            QueueEntryKind::Review => self.review_count += 1,
            QueueEntryKind::Learning => unreachable!(),
        }
        self.main.push_front(entry);
    }
}
