// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashSet;

use super::Backend;
use crate::{
    backend_proto::{self as pb},
    cloze::add_cloze_numbers_in_string,
    prelude::*,
};
pub(super) use pb::notes_service::Service as NotesService;

impl NotesService for Backend {
    // notes
    //-------------------------------------------------------------------

    fn new_note(&self, input: pb::NoteTypeId) -> Result<pb::Note> {
        self.with_col(|col| {
            let nt = col.get_notetype(input.into())?.ok_or(AnkiError::NotFound)?;
            Ok(nt.new_note().into())
        })
    }

    fn add_note(&self, input: pb::AddNoteIn) -> Result<pb::NoteId> {
        self.with_col(|col| {
            let mut note: Note = input.note.ok_or(AnkiError::NotFound)?.into();
            col.add_note(&mut note, DeckID(input.deck_id))
                .map(|_| pb::NoteId { nid: note.id.0 })
        })
    }

    fn defaults_for_adding(&self, input: pb::DefaultsForAddingIn) -> Result<pb::DeckAndNotetype> {
        self.with_col(|col| {
            let home_deck: DeckID = input.home_deck_of_current_review_card.into();
            col.defaults_for_adding(home_deck).map(Into::into)
        })
    }

    fn default_deck_for_notetype(&self, input: pb::NoteTypeId) -> Result<pb::DeckId> {
        self.with_col(|col| {
            Ok(col
                .default_deck_for_notetype(input.into())?
                .unwrap_or(DeckID(0))
                .into())
        })
    }

    fn update_note(&self, input: pb::UpdateNoteIn) -> Result<pb::Empty> {
        self.with_col(|col| {
            let op = if input.skip_undo_entry {
                None
            } else {
                Some(Op::UpdateNote)
            };
            let mut note: Note = input.note.ok_or(AnkiError::NotFound)?.into();
            col.update_note_with_op(&mut note, op)
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

    fn remove_notes(&self, input: pb::RemoveNotesIn) -> Result<pb::Empty> {
        self.with_col(|col| {
            if !input.note_ids.is_empty() {
                col.remove_notes(
                    &input
                        .note_ids
                        .into_iter()
                        .map(Into::into)
                        .collect::<Vec<_>>(),
                )?;
            }
            if !input.card_ids.is_empty() {
                let nids = col.storage.note_ids_of_cards(
                    &input
                        .card_ids
                        .into_iter()
                        .map(Into::into)
                        .collect::<Vec<_>>(),
                )?;
                col.remove_notes(&nids.into_iter().collect::<Vec<_>>())?
            }
            Ok(().into())
        })
    }

    fn add_note_tags(&self, input: pb::AddNoteTagsIn) -> Result<pb::UInt32> {
        self.with_col(|col| {
            col.add_tags_to_notes(&to_note_ids(input.nids), &input.tags)
                .map(|n| n as u32)
        })
        .map(Into::into)
    }

    fn update_note_tags(&self, input: pb::UpdateNoteTagsIn) -> Result<pb::UInt32> {
        self.with_col(|col| {
            col.replace_tags_for_notes(
                &to_note_ids(input.nids),
                &input.tags,
                &input.replacement,
                input.regex,
            )
            .map(|n| (n as u32).into())
        })
    }

    fn cloze_numbers_in_note(&self, note: pb::Note) -> Result<pb::ClozeNumbersInNoteOut> {
        let mut set = HashSet::with_capacity(4);
        for field in &note.fields {
            add_cloze_numbers_in_string(field, &mut set);
        }
        Ok(pb::ClozeNumbersInNoteOut {
            numbers: set.into_iter().map(|n| n as u32).collect(),
        })
    }

    fn after_note_updates(&self, input: pb::AfterNoteUpdatesIn) -> Result<pb::Empty> {
        self.with_col(|col| {
            col.transact(None, |col| {
                col.after_note_updates(
                    &to_note_ids(input.nids),
                    input.generate_cards,
                    input.mark_notes_modified,
                )?;
                Ok(pb::Empty {})
            })
        })
    }

    fn field_names_for_notes(
        &self,
        input: pb::FieldNamesForNotesIn,
    ) -> Result<pb::FieldNamesForNotesOut> {
        self.with_col(|col| {
            let nids: Vec<_> = input.nids.into_iter().map(NoteID).collect();
            col.storage
                .field_names_for_notes(&nids)
                .map(|fields| pb::FieldNamesForNotesOut { fields })
        })
    }

    fn note_is_duplicate_or_empty(&self, input: pb::Note) -> Result<pb::NoteIsDuplicateOrEmptyOut> {
        let note: Note = input.into();
        self.with_col(|col| {
            col.note_is_duplicate_or_empty(&note)
                .map(|r| pb::NoteIsDuplicateOrEmptyOut { state: r as i32 })
        })
    }

    fn cards_of_note(&self, input: pb::NoteId) -> Result<pb::CardIDs> {
        self.with_col(|col| {
            col.storage
                .all_card_ids_of_note(NoteID(input.nid))
                .map(|v| pb::CardIDs {
                    cids: v.into_iter().map(Into::into).collect(),
                })
        })
    }
}

fn to_note_ids(ids: Vec<i64>) -> Vec<NoteID> {
    ids.into_iter().map(NoteID).collect()
}
