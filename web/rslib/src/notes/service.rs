// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use crate::cloze::cloze_number_in_fields;
use crate::collection::Collection;
use crate::decks::DeckId;
use crate::error;
use crate::error::AnkiError;
use crate::error::OrInvalid;
use crate::error::OrNotFound;
use crate::notes::AddNoteRequest;
use crate::notes::Note;
use crate::notes::NoteId;
use crate::prelude::IntoNewtypeVec;

pub(crate) fn to_i64s(ids: Vec<NoteId>) -> Vec<i64> {
    ids.into_iter().map(Into::into).collect()
}

impl crate::services::NotesService for Collection {
    fn new_note(
        &mut self,
        input: anki_proto::notetypes::NotetypeId,
    ) -> error::Result<anki_proto::notes::Note> {
        let ntid = input.into();

        let nt = self.get_notetype(ntid)?.or_not_found(ntid)?;
        Ok(nt.new_note().into())
    }

    fn add_note(
        &mut self,
        input: anki_proto::notes::AddNoteRequest,
    ) -> error::Result<anki_proto::notes::AddNoteResponse> {
        let mut note: Note = input.note.or_invalid("no note provided")?.into();
        let changes = self.add_note(&mut note, DeckId(input.deck_id))?;
        Ok(anki_proto::notes::AddNoteResponse {
            note_id: note.id.0,
            changes: Some(changes.into()),
        })
    }

    fn add_notes(
        &mut self,
        input: anki_proto::notes::AddNotesRequest,
    ) -> error::Result<anki_proto::notes::AddNotesResponse> {
        let mut requests = input
            .requests
            .into_iter()
            .map(TryInto::try_into)
            .collect::<error::Result<Vec<AddNoteRequest>, AnkiError>>()?;
        let changes = self.add_notes(&mut requests)?;
        Ok(anki_proto::notes::AddNotesResponse {
            nids: requests.iter().map(|r| r.note.id.0).collect(),
            changes: Some(changes.into()),
        })
    }

    fn defaults_for_adding(
        &mut self,
        input: anki_proto::notes::DefaultsForAddingRequest,
    ) -> error::Result<anki_proto::notes::DeckAndNotetype> {
        let home_deck: DeckId = input.home_deck_of_current_review_card.into();
        self.defaults_for_adding(home_deck).map(Into::into)
    }

    fn default_deck_for_notetype(
        &mut self,
        input: anki_proto::notetypes::NotetypeId,
    ) -> error::Result<anki_proto::decks::DeckId> {
        Ok(self
            .default_deck_for_notetype(input.into())?
            .unwrap_or(DeckId(0))
            .into())
    }

    fn update_notes(
        &mut self,
        input: anki_proto::notes::UpdateNotesRequest,
    ) -> error::Result<anki_proto::collection::OpChanges> {
        let notes = input
            .notes
            .into_iter()
            .map(Into::into)
            .collect::<Vec<Note>>();
        self.update_notes_maybe_undoable(notes, !input.skip_undo_entry)
            .map(Into::into)
    }

    fn get_note(
        &mut self,
        input: anki_proto::notes::NoteId,
    ) -> error::Result<anki_proto::notes::Note> {
        let nid = input.into();
        self.storage
            .get_note(nid)?
            .or_not_found(nid)
            .map(Into::into)
    }

    fn remove_notes(
        &mut self,
        input: anki_proto::notes::RemoveNotesRequest,
    ) -> error::Result<anki_proto::collection::OpChangesWithCount> {
        if !input.note_ids.is_empty() {
            self.remove_notes(
                &input
                    .note_ids
                    .into_iter()
                    .map(Into::into)
                    .collect::<Vec<_>>(),
            )
        } else {
            let nids = self.storage.note_ids_of_cards(
                &input
                    .card_ids
                    .into_iter()
                    .map(Into::into)
                    .collect::<Vec<_>>(),
            )?;
            self.remove_notes(&nids.into_iter().collect::<Vec<_>>())
        }
        .map(Into::into)
    }

    fn cloze_numbers_in_note(
        &mut self,
        note: anki_proto::notes::Note,
    ) -> error::Result<anki_proto::notes::ClozeNumbersInNoteResponse> {
        let set = cloze_number_in_fields(note.fields);
        Ok(anki_proto::notes::ClozeNumbersInNoteResponse {
            numbers: set.into_iter().map(|n| n as u32).collect(),
        })
    }

    fn after_note_updates(
        &mut self,
        input: anki_proto::notes::AfterNoteUpdatesRequest,
    ) -> error::Result<anki_proto::collection::OpChangesWithCount> {
        self.after_note_updates(
            &to_note_ids(input.nids),
            input.generate_cards,
            input.mark_notes_modified,
        )
        .map(Into::into)
    }

    fn field_names_for_notes(
        &mut self,
        input: anki_proto::notes::FieldNamesForNotesRequest,
    ) -> error::Result<anki_proto::notes::FieldNamesForNotesResponse> {
        let nids: Vec<_> = input.nids.into_iter().map(NoteId).collect();
        self.storage
            .field_names_for_notes(&nids)
            .map(|fields| anki_proto::notes::FieldNamesForNotesResponse { fields })
    }

    fn note_fields_check(
        &mut self,
        input: anki_proto::notes::Note,
    ) -> error::Result<anki_proto::notes::NoteFieldsCheckResponse> {
        let note: Note = input.into();

        self.note_fields_check(&note)
            .map(|r| anki_proto::notes::NoteFieldsCheckResponse { state: r as i32 })
    }

    fn cards_of_note(
        &mut self,
        input: anki_proto::notes::NoteId,
    ) -> error::Result<anki_proto::cards::CardIds> {
        self.storage
            .all_card_ids_of_note_in_template_order(NoteId(input.nid))
            .map(|v| anki_proto::cards::CardIds {
                cids: v.into_iter().map(Into::into).collect(),
            })
    }

    fn get_single_notetype_of_notes(
        &mut self,
        input: anki_proto::notes::NoteIds,
    ) -> error::Result<anki_proto::notetypes::NotetypeId> {
        self.get_single_notetype_of_notes(&input.note_ids.into_newtype(NoteId))
            .map(Into::into)
    }
}

pub(crate) fn to_note_ids(ids: Vec<i64>) -> Vec<NoteId> {
    ids.into_iter().map(NoteId).collect()
}

impl From<anki_proto::notes::NoteId> for NoteId {
    fn from(nid: anki_proto::notes::NoteId) -> Self {
        NoteId(nid.nid)
    }
}

impl From<NoteId> for anki_proto::notes::NoteId {
    fn from(nid: NoteId) -> Self {
        anki_proto::notes::NoteId { nid: nid.0 }
    }
}
