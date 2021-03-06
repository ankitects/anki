// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::prelude::*;
use crate::undo::Undo;

#[derive(Debug)]
pub(crate) struct NoteAdded(Note);

impl Undo for NoteAdded {
    fn undo(self: Box<Self>, col: &mut Collection) -> Result<()> {
        col.remove_note_for_undo(self.0)
    }
}

#[derive(Debug)]
pub(crate) struct NoteRemoved(Note);

impl Undo for NoteRemoved {
    fn undo(self: Box<Self>, col: &mut Collection) -> Result<()> {
        col.add_note_for_undo(self.0)
    }
}

#[derive(Debug)]
pub(crate) struct NoteGraveAdded(NoteID, Usn);

impl Undo for NoteGraveAdded {
    fn undo(self: Box<Self>, col: &mut Collection) -> Result<()> {
        col.remove_note_grave_for_undo(self.0, self.1)
    }
}

#[derive(Debug)]
pub(crate) struct NoteGraveRemoved(NoteID, Usn);

impl Undo for NoteGraveRemoved {
    fn undo(self: Box<Self>, col: &mut Collection) -> Result<()> {
        col.add_note_grave(self.0, self.1)
    }
}

#[derive(Debug)]
pub(crate) struct NoteUpdated(Note);

impl Undo for NoteUpdated {
    fn undo(self: Box<Self>, col: &mut Collection) -> Result<()> {
        let current = col
            .storage
            .get_note(self.0.id)?
            .ok_or_else(|| AnkiError::invalid_input("note disappeared"))?;
        col.update_note_undoable(&mut self.0.clone(), &current)
    }
}

impl Collection {
    /// Saves in the undo queue, and commits to DB.
    /// No validation, card generation or normalization is done.
    pub(super) fn update_note_undoable(&mut self, note: &mut Note, original: &Note) -> Result<()> {
        self.save_undo(Box::new(NoteUpdated(original.clone())));
        self.storage.update_note(note)?;

        Ok(())
    }

    /// Remove a note. Cards must already have been deleted.
    pub(crate) fn remove_note_only_undoable(&mut self, nid: NoteID, usn: Usn) -> Result<()> {
        if let Some(note) = self.storage.get_note(nid)? {
            self.save_undo(Box::new(NoteRemoved(note)));
            self.storage.remove_note(nid)?;
            self.add_note_grave(nid, usn)?;
        }
        Ok(())
    }

    /// Add a note, not adding any cards.
    pub(super) fn add_note_only_undoable(&mut self, note: &mut Note) -> Result<(), AnkiError> {
        self.storage.add_note(note)?;
        self.save_undo(Box::new(NoteAdded(note.clone())));
        Ok(())
    }

    fn add_note_grave(&mut self, nid: NoteID, usn: Usn) -> Result<()> {
        self.save_undo(Box::new(NoteGraveAdded(nid, usn)));
        self.storage.add_note_grave(nid, usn)
    }

    fn remove_note_grave_for_undo(&mut self, nid: NoteID, usn: Usn) -> Result<()> {
        self.save_undo(Box::new(NoteGraveRemoved(nid, usn)));
        self.storage.remove_note_grave(nid)
    }

    fn remove_note_for_undo(&mut self, note: Note) -> Result<()> {
        self.storage.remove_note(note.id)?;
        self.save_undo(Box::new(NoteRemoved(note)));
        Ok(())
    }

    fn add_note_for_undo(&mut self, note: Note) -> Result<()> {
        self.storage.add_or_update_note(&note)?;
        self.save_undo(Box::new(NoteAdded(note)));
        Ok(())
    }
}
