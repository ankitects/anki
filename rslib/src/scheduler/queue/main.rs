// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::CardQueues;
use crate::prelude::*;

#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct MainQueueEntry {
    pub id: CardID,
    pub mtime: TimestampSecs,
    pub kind: MainQueueEntryKind,
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) enum MainQueueEntryKind {
    New,
    Review,
}

impl CardQueues {
    pub(super) fn next_main_entry(&self) -> Option<MainQueueEntry> {
        self.main.front().copied()
    }

    pub(super) fn pop_main_entry(&mut self, id: CardID) -> Option<MainQueueEntry> {
        if let Some(last) = self.main.front() {
            if last.id == id {
                match last.kind {
                    MainQueueEntryKind::New => self.counts.new -= 1,
                    MainQueueEntryKind::Review => self.counts.review -= 1,
                }
                return self.main.pop_front();
            }
        }

        None
    }
}
