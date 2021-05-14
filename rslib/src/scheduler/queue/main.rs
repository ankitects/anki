// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::CardQueues;
use crate::prelude::*;

#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct MainQueueEntry {
    pub id: CardId,
    pub mtime: TimestampSecs,
    pub kind: MainQueueEntryKind,
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) enum MainQueueEntryKind {
    New,
    Review,
}

impl CardQueues {
    /// Remove the head of the main queue, and update counts.
    pub(super) fn pop_main(&mut self) -> Option<MainQueueEntry> {
        self.main.pop_front().map(|head| {
            match head.kind {
                MainQueueEntryKind::New => self.counts.new -= 1,
                MainQueueEntryKind::Review => self.counts.review -= 1,
            };
            head
        })
    }

    /// Add an undone entry to the top of the main queue.
    pub(super) fn push_main(&mut self, entry: MainQueueEntry) {
        match entry.kind {
            MainQueueEntryKind::New => self.counts.new += 1,
            MainQueueEntryKind::Review => self.counts.review += 1,
        };
        self.main.push_front(entry);
    }
}
