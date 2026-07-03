// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::NoteTags;
use crate::collection::undo::UndoableCollectionChange;
use crate::prelude::*;
use crate::undo::UndoableChange;

#[derive(Debug)]
pub(crate) enum UndoableNoteChange {
    Added(Box<Note>),
    Updated(Box<Note>),
    Removed(Box<Note>),
    GraveAdded(Box<(NoteId, Usn)>),
    GraveRemoved(Box<(NoteId, Usn)>),
    TagsUpdated(Box<NoteTags>),
}

impl Collection {
    pub(crate) fn undo_note_change(&mut self, change: UndoableNoteChange) -> Result<()> {
        match change {
            UndoableNoteChange::Added(note) => self.remove_note_without_grave(*note),
            UndoableNoteChange::Updated(note) => {
                let current = self
                    .storage
                    .get_note(note.id)?
                    .or_invalid("note disappeared")?;
                self.update_note_undoable(&note, &current)
            }
            UndoableNoteChange::Removed(note) => self.restore_deleted_note(*note),
            UndoableNoteChange::GraveAdded(e) => self.remove_note_grave(e.0, e.1),
            UndoableNoteChange::GraveRemoved(e) => self.add_note_grave(e.0, e.1),
            UndoableNoteChange::TagsUpdated(note_tags) => {
                let current = self
                    .storage
                    .get_note_tags_by_id(note_tags.id)?
                    .or_invalid("note disappeared")?;
                self.update_note_tags_undoable(&note_tags, current)
            }
        }
    }

    /// Saves in the undo queue, and commits to DB.
    /// No validation, card generation or normalization is done.
    pub(crate) fn update_note_undoable(&mut self, note: &Note, original: &Note) -> Result<()> {
        self.save_undo(UndoableNoteChange::Updated(Box::new(original.clone())));
        self.storage.update_note(note)?;

        Ok(())
    }

    /// Remove a note. Cards must already have been deleted.
    pub(crate) fn remove_note_only_undoable(&mut self, nid: NoteId, usn: Usn) -> Result<()> {
        if let Some(note) = self.storage.get_note(nid)? {
            self.save_undo(UndoableNoteChange::Removed(Box::new(note)));
            self.storage.remove_note(nid)?;
            self.add_note_grave(nid, usn)?;
        }
        Ok(())
    }

    /// If note is edited multiple times in quick succession, avoid creating
    /// extra undo entries.
    pub(crate) fn maybe_coalesce_note_undo_entry(&mut self, changes: &OpChanges) {
        if changes.op != Op::UpdateNote {
            return;
        }
        let Some(previous_op) = self.previous_undo_op() else {
            return;
        };
        if previous_op.kind != Op::UpdateNote {
            return;
        }
        let Some(current_op) = self.current_undo_op() else {
            return;
        };
        if let (
            [UndoableChange::Note(UndoableNoteChange::Updated(previous)), UndoableChange::Collection(UndoableCollectionChange::Modified(_))],
            [UndoableChange::Note(UndoableNoteChange::Updated(current)), UndoableChange::Collection(UndoableCollectionChange::Modified(_))],
        ) = (&previous_op.changes[..], &current_op.changes[..])
        {
            if previous.id == current.id && previous_op.timestamp.elapsed_secs() < 60 {
                self.clear_last_op();
            }
        }
    }

    /// Add a note, not adding any cards.
    pub(crate) fn add_note_only_undoable(&mut self, note: &mut Note) -> Result<(), AnkiError> {
        self.storage.add_note(note)?;
        self.save_undo(UndoableNoteChange::Added(Box::new(note.clone())));

        Ok(())
    }

    /// Add a note, not adding any cards. Caller guarantees id is unique.
    pub(crate) fn add_note_only_with_id_undoable(&mut self, note: &mut Note) -> Result<()> {
        require!(self.storage.add_note_if_unique(note)?, "note id existed");
        self.save_undo(UndoableNoteChange::Added(Box::new(note.clone())));
        Ok(())
    }

    pub(crate) fn update_note_tags_undoable(
        &mut self,
        tags: &NoteTags,
        original: NoteTags,
    ) -> Result<()> {
        self.save_undo(UndoableNoteChange::TagsUpdated(Box::new(original)));
        self.storage.update_note_tags(tags)
    }

    fn remove_note_without_grave(&mut self, note: Note) -> Result<()> {
        self.storage.remove_note(note.id)?;
        self.save_undo(UndoableNoteChange::Removed(Box::new(note)));
        Ok(())
    }

    fn restore_deleted_note(&mut self, note: Note) -> Result<()> {
        self.storage.add_or_update_note(&note)?;
        self.save_undo(UndoableNoteChange::Added(Box::new(note)));
        Ok(())
    }

    fn add_note_grave(&mut self, nid: NoteId, usn: Usn) -> Result<()> {
        self.save_undo(UndoableNoteChange::GraveAdded(Box::new((nid, usn))));
        self.storage.add_note_grave(nid, usn)
    }

    fn remove_note_grave(&mut self, nid: NoteId, usn: Usn) -> Result<()> {
        self.save_undo(UndoableNoteChange::GraveRemoved(Box::new((nid, usn))));
        self.storage.remove_note_grave(nid)
    }
}
