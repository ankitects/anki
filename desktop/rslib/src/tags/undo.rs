// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::Tag;
use crate::prelude::*;

#[derive(Debug)]
pub(crate) enum UndoableTagChange {
    Added(Box<Tag>),
    Removed(Box<Tag>),
    Updated(Box<Tag>),
}

impl Collection {
    pub(crate) fn undo_tag_change(&mut self, change: UndoableTagChange) -> Result<()> {
        match change {
            UndoableTagChange::Added(tag) => self.remove_single_tag_undoable(*tag),
            UndoableTagChange::Removed(tag) => self.register_tag_undoable(&tag),
            UndoableTagChange::Updated(tag) => {
                let current = self
                    .storage
                    .get_tag(&tag.name)?
                    .or_invalid("tag disappeared")?;
                self.update_tag_undoable(&tag, current)
            }
        }
    }

    /// Updates an existing tag, saving an undo entry. Caller must update usn.
    pub(super) fn update_tag_undoable(&mut self, tag: &Tag, original: Tag) -> Result<()> {
        self.save_undo(UndoableTagChange::Updated(Box::new(original)));
        self.storage.update_tag(tag)
    }

    /// Adds an already-validated tag to the tag list, saving an undo entry.
    /// Caller is responsible for setting usn.
    pub(super) fn register_tag_undoable(&mut self, tag: &Tag) -> Result<()> {
        self.save_undo(UndoableTagChange::Added(Box::new(tag.clone())));
        self.storage.register_tag(tag)
    }

    /// Remove a single tag from the tag list, saving an undo entry. Does not
    /// alter notes. FIXME: caller will need to update usn when we make tags
    /// incrementally syncable.
    pub(super) fn remove_single_tag_undoable(&mut self, tag: Tag) -> Result<()> {
        self.storage.remove_single_tag(&tag.name)?;
        self.save_undo(UndoableTagChange::Removed(Box::new(tag)));
        Ok(())
    }
}
