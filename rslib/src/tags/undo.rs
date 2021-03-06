// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::Tag;
use crate::prelude::*;

#[derive(Debug)]
pub(crate) enum UndoableTagChange {
    Added(Box<Tag>),
    Removed(Box<Tag>),
}

impl Collection {
    pub(crate) fn undo_tag_change(&mut self, change: UndoableTagChange) -> Result<()> {
        match change {
            UndoableTagChange::Added(tag) => self.remove_single_tag_undoable(&tag),
            UndoableTagChange::Removed(tag) => self.register_tag_undoable(&tag),
        }
    }
    /// Adds an already-validated tag to the DB and undo list.
    /// Caller is responsible for setting usn.
    pub(super) fn register_tag_undoable(&mut self, tag: &Tag) -> Result<()> {
        self.save_undo(UndoableTagChange::Added(Box::new(tag.clone())));
        self.storage.register_tag(&tag)
    }

    fn remove_single_tag_undoable(&mut self, tag: &Tag) -> Result<()> {
        self.save_undo(UndoableTagChange::Removed(Box::new(tag.clone())));
        self.storage.remove_single_tag(&tag.name)
    }
}
