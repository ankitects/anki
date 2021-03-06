// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::Tag;
use crate::{prelude::*, undo::Undo};

#[derive(Debug)]
struct AddedTag(Tag);

#[derive(Debug)]
struct RemovedTag(Tag);

impl Undo for AddedTag {
    fn undo(self: Box<Self>, col: &mut Collection) -> Result<()> {
        col.remove_single_tag_undoable(&self.0)
    }
}

impl Undo for RemovedTag {
    fn undo(self: Box<Self>, col: &mut Collection) -> Result<()> {
        col.register_tag_undoable(&self.0)
    }
}

impl Collection {
    /// Adds an already-validated tag to the DB and undo list.
    /// Caller is responsible for setting usn.
    pub(super) fn register_tag_undoable(&mut self, tag: &Tag) -> Result<()> {
        self.save_undo(Box::new(AddedTag(tag.clone())));
        self.storage.register_tag(&tag)
    }

    fn remove_single_tag_undoable(&mut self, tag: &Tag) -> Result<()> {
        self.save_undo(Box::new(RemovedTag(tag.clone())));
        self.storage.remove_single_tag(&tag.name)
    }
}
