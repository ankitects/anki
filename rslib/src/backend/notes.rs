// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashSet;

use super::Backend;
pub(super) use crate::backend_proto::notes_service::Service as NotesService;
use crate::{
    backend_proto::{self as pb},
    cloze::add_cloze_numbers_in_string,
    prelude::*,
};

impl NotesService for Backend {
    fn new_note(&self, input: pb::NotetypeId) -> Result<pb::Note> {
        self.with_col(|col| {
            let nt = col.get_notetype(input.into())?.ok_or(AnkiError::NotFound)?;
            Ok(nt.new_note().into())
        })
    }

    fn add_note(&self, input: pb::AddNoteRequest) -> Result<pb::AddNoteResponse> {
        self.with_col(|col| {
            let mut note: Note = input.note.ok_or(AnkiError::NotFound)?.into();
            let changes = col.add_note(&mut note, DeckId(input.deck_id))?;
            Ok(pb::AddNoteResponse {
                note_id: note.id.0,
                changes: Some(changes.into()),
            })
        })
    }

    fn defaults_for_adding(
        &self,
        input: pb::DefaultsForAddingRequest,
    ) -> Result<pb::DeckAndNotetype> {
        self.with_col(|col| {
            let home_deck: DeckId = input.home_deck_of_current_review_card.into();
            col.defaults_for_adding(home_deck).map(Into::into)
        })
    }

    fn default_deck_for_notetype(&self, input: pb::NotetypeId) -> Result<pb::DeckId> {
        self.with_col(|col| {
            Ok(col
                .default_deck_for_notetype(input.into())?
                .unwrap_or(DeckId(0))
                .into())
        })
    }

    fn update_notes(&self, input: pb::UpdateNotesRequest) -> Result<pb::OpChanges> {
        self.with_col(|col| {
            let notes = input
                .notes
                .into_iter()
                .map(Into::into)
                .collect::<Vec<Note>>();
            col.update_notes_maybe_undoable(notes, !input.skip_undo_entry)
        })
        .map(Into::into)
    }

    fn get_note(&self, input: pb::NoteId) -> Result<pb::Note> {
        self.with_col(|col| {
            col.storage
                .get_note(input.into())?
                .ok_or(AnkiError::NotFound)
                .map(Into::into)
        })
    }

    fn remove_notes(&self, input: pb::RemoveNotesRequest) -> Result<pb::OpChangesWithCount> {
        self.with_col(|col| {
            if !input.note_ids.is_empty() {
                col.remove_notes(
                    &input
                        .note_ids
                        .into_iter()
                        .map(Into::into)
                        .collect::<Vec<_>>(),
                )
            } else {
                let nids = col.storage.note_ids_of_cards(
                    &input
                        .card_ids
                        .into_iter()
                        .map(Into::into)
                        .collect::<Vec<_>>(),
                )?;
                col.remove_notes(&nids.into_iter().collect::<Vec<_>>())
            }
            .map(Into::into)
        })
    }

    fn cloze_numbers_in_note(&self, note: pb::Note) -> Result<pb::ClozeNumbersInNoteResponse> {
        let mut set = HashSet::with_capacity(4);
        for field in &note.fields {
            add_cloze_numbers_in_string(field, &mut set);
        }
        Ok(pb::ClozeNumbersInNoteResponse {
            numbers: set.into_iter().map(|n| n as u32).collect(),
        })
    }

    fn after_note_updates(
        &self,
        input: pb::AfterNoteUpdatesRequest,
    ) -> Result<pb::OpChangesWithCount> {
        self.with_col(|col| {
            col.after_note_updates(
                &to_note_ids(input.nids),
                input.generate_cards,
                input.mark_notes_modified,
            )
            .map(Into::into)
        })
    }

    fn field_names_for_notes(
        &self,
        input: pb::FieldNamesForNotesRequest,
    ) -> Result<pb::FieldNamesForNotesResponse> {
        self.with_col(|col| {
            let nids: Vec<_> = input.nids.into_iter().map(NoteId).collect();
            col.storage
                .field_names_for_notes(&nids)
                .map(|fields| pb::FieldNamesForNotesResponse { fields })
        })
    }

    fn note_fields_check(&self, input: pb::Note) -> Result<pb::NoteFieldsCheckResponse> {
        let note: Note = input.into();
        self.with_col(|col| {
            col.note_fields_check(&note)
                .map(|r| pb::NoteFieldsCheckResponse { state: r as i32 })
        })
    }

    fn cards_of_note(&self, input: pb::NoteId) -> Result<pb::CardIds> {
        self.with_col(|col| {
            col.storage
                .all_card_ids_of_note_in_template_order(NoteId(input.nid))
                .map(|v| pb::CardIds {
                    cids: v.into_iter().map(Into::into).collect(),
                })
        })
    }

    fn get_single_notetype_of_notes(&self, input: pb::NoteIds) -> Result<pb::NotetypeId> {
        self.with_col(|col| {
            col.get_single_notetype_of_notes(&input.note_ids.into_newtype(NoteId))
                .map(Into::into)
        })
    }
}

pub(super) fn to_note_ids(ids: Vec<i64>) -> Vec<NoteId> {
    ids.into_iter().map(NoteId).collect()
}
