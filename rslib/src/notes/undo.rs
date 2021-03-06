// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{prelude::*, undo::UndoableChange};

#[derive(Debug)]
pub(crate) enum UndoableNoteChange {
    Added(Box<Note>),
    Updated(Box<Note>),
    Removed(Box<Note>),
    GraveAdded(Box<(NoteID, Usn)>),
    GraveRemoved(Box<(NoteID, Usn)>),
}

impl Collection {
    pub(crate) fn undo_note_change(&mut self, change: UndoableNoteChange) -> Result<()> {
        match change {
            UndoableNoteChange::Added(note) => self.remove_note_without_grave(*note),
            UndoableNoteChange::Updated(note) => {
                let current = self
                    .storage
                    .get_note(note.id)?
                    .ok_or_else(|| AnkiError::invalid_input("note disappeared"))?;
                self.update_note_undoable(&note, &current, false)
            }
            UndoableNoteChange::Removed(note) => self.restore_deleted_note(*note),
            UndoableNoteChange::GraveAdded(e) => self.remove_note_grave(e.0, e.1),
            UndoableNoteChange::GraveRemoved(e) => self.add_note_grave(e.0, e.1),
        }
    }

    /// Saves in the undo queue, and commits to DB.
    /// No validation, card generation or normalization is done.
    /// If `coalesce_updates` is true, successive updates within a 1 minute
    /// period will not result in further undo entries.
    pub(super) fn update_note_undoable(
        &mut self,
        note: &Note,
        original: &Note,
        coalesce_updates: bool,
    ) -> Result<()> {
        if !coalesce_updates || !self.note_was_just_updated(note) {
            self.save_undo(UndoableNoteChange::Updated(Box::new(original.clone())));
        }
        self.storage.update_note(note)?;

        Ok(())
    }

    /// Remove a note. Cards must already have been deleted.
    pub(crate) fn remove_note_only_undoable(&mut self, nid: NoteID, usn: Usn) -> Result<()> {
        if let Some(note) = self.storage.get_note(nid)? {
            self.save_undo(UndoableNoteChange::Removed(Box::new(note)));
            self.storage.remove_note(nid)?;
            self.add_note_grave(nid, usn)?;
        }
        Ok(())
    }

    /// Add a note, not adding any cards.
    pub(super) fn add_note_only_undoable(&mut self, note: &mut Note) -> Result<(), AnkiError> {
        self.storage.add_note(note)?;
        self.save_undo(UndoableNoteChange::Added(Box::new(note.clone())));

        Ok(())
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

    fn add_note_grave(&mut self, nid: NoteID, usn: Usn) -> Result<()> {
        self.save_undo(UndoableNoteChange::GraveAdded(Box::new((nid, usn))));
        self.storage.add_note_grave(nid, usn)
    }

    fn remove_note_grave(&mut self, nid: NoteID, usn: Usn) -> Result<()> {
        self.save_undo(UndoableNoteChange::GraveRemoved(Box::new((nid, usn))));
        self.storage.remove_note_grave(nid)
    }

    /// True only if the last operation was UpdateNote, and the same note was just updated less than
    /// a minute ago.
    fn note_was_just_updated(&self, before_change: &Note) -> bool {
        self.previous_undo_op()
            .map(|op| {
                if let Some(UndoableChange::Note(UndoableNoteChange::Updated(note))) =
                    op.changes.last()
                {
                    note.id == before_change.id
                        && op.kind == UndoableOpKind::UpdateNote
                        && op.timestamp.elapsed_secs() < 60
                } else {
                    false
                }
            })
            .unwrap_or(false)
    }
}
