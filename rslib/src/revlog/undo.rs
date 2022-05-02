// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::RevlogEntry;
use crate::prelude::*;

#[derive(Debug)]
pub(crate) enum UndoableRevlogChange {
    Added(Box<RevlogEntry>),
    Removed(Box<RevlogEntry>),
}

impl Collection {
    pub(crate) fn undo_revlog_change(&mut self, change: UndoableRevlogChange) -> Result<()> {
        match change {
            UndoableRevlogChange::Added(revlog) => {
                self.storage.remove_revlog_entry(revlog.id)?;
                self.save_undo(UndoableRevlogChange::Removed(revlog));
                Ok(())
            }
            UndoableRevlogChange::Removed(revlog) => {
                self.storage.add_revlog_entry(&revlog, false)?;
                self.save_undo(UndoableRevlogChange::Added(revlog));
                Ok(())
            }
        }
    }

    /// Add the provided revlog entry, modifying the ID if it is not unique.
    pub(crate) fn add_revlog_entry_undoable(&mut self, mut entry: RevlogEntry) -> Result<RevlogId> {
        entry.id = self.storage.add_revlog_entry(&entry, true)?.unwrap();
        let id = entry.id;
        self.save_undo(UndoableRevlogChange::Added(Box::new(entry)));
        Ok(id)
    }

    /// Add the provided revlog entry, if its ID is unique.
    pub(crate) fn add_revlog_entry_if_unique_undoable(&mut self, entry: RevlogEntry) -> Result<()> {
        if self.storage.add_revlog_entry(&entry, false)?.is_some() {
            self.save_undo(UndoableRevlogChange::Added(Box::new(entry)));
        }
        Ok(())
    }
}
