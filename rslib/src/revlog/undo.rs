// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::RevlogEntry;
use crate::{prelude::*, undo::Undo};

#[derive(Debug)]
pub(crate) struct RevlogAdded(RevlogEntry);
#[derive(Debug)]
pub(crate) struct RevlogRemoved(RevlogEntry);

impl Undo for RevlogAdded {
    fn undo(self: Box<Self>, col: &mut Collection) -> Result<()> {
        col.storage.remove_revlog_entry(self.0.id)?;
        col.save_undo(Box::new(RevlogRemoved(self.0)));
        Ok(())
    }
}

impl Undo for RevlogRemoved {
    fn undo(self: Box<Self>, col: &mut Collection) -> Result<()> {
        col.storage.add_revlog_entry(&self.0, false)?;
        col.save_undo(Box::new(RevlogAdded(self.0)));
        Ok(())
    }
}

impl Collection {
    /// Add the provided revlog entry, modifying the ID if it is not unique.
    pub(crate) fn add_revlog_entry_undoable(&mut self, mut entry: RevlogEntry) -> Result<RevlogID> {
        entry.id = self.storage.add_revlog_entry(&entry, true)?;
        let id = entry.id;
        self.save_undo(Box::new(RevlogAdded(entry)));
        Ok(id)
    }
}
