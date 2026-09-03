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

#[cfg(test)]
mod test {

    use std::assert_matches;
    use std::collections::HashSet;

    use anki_proto::notes::*;

    use crate::collection::Collection;
    use crate::prelude::*;
    use crate::services::NotesService;

    #[test]
    fn note_added() {
        let mut col = Collection::new();
        let nt = col.basic_notetype();

        // Note ID and changes are returned
        let note = NotesService::new_note(&mut col, nt.id.into()).unwrap();
        let response = NotesService::add_note(
            &mut col,
            AddNoteRequest {
                note: Some(note),
                deck_id: 1,
            },
        )
        .unwrap();
        assert_ne!(response.note_id, 0);
        assert_ne!(response.changes, None);

        // No note passed
        let result = NotesService::add_note(
            &mut col,
            AddNoteRequest {
                note: None,
                deck_id: 1,
            },
        );
        assert_matches!(result, Err(AnkiError::InvalidInput { .. }));
    }

    #[test]
    fn bulk_notes_added() {
        let mut col = Collection::new();
        let nt = col.basic_notetype();
        let requests: Vec<_> = (0..10)
            .map(|_| {
                let note = NotesService::new_note(&mut col, nt.id.into()).unwrap();
                AddNoteRequest {
                    note: Some(note),
                    deck_id: 1,
                }
            })
            .collect();
        let response = NotesService::add_notes(&mut col, AddNotesRequest { requests }).unwrap();
        assert_eq!(response.nids.len(), 10);
        assert_ne!(response.changes, None);
    }

    #[test]
    fn adding_defaults() {
        let mut col = Collection::new();
        let response = NotesService::defaults_for_adding(
            &mut col,
            DefaultsForAddingRequest {
                home_deck_of_current_review_card: 1,
            },
        )
        .unwrap();
        assert_eq!(response.deck_id, 1);
        assert_eq!(response.notetype_id, col.basic_notetype().id.0);
    }

    #[test]
    fn default_deck_for_notetype() {
        let mut col = Collection::new();
        let nt = col.basic_notetype();
        // No deck set; return 0
        let response = NotesService::default_deck_for_notetype(&mut col, nt.id.into()).unwrap();
        assert_eq!(response, DeckId(0).into());

        col.set_last_deck_for_notetype(nt.id, DeckId(1)).unwrap();
        let response = NotesService::default_deck_for_notetype(&mut col, nt.id.into()).unwrap();
        assert_eq!(response, DeckId(1).into());
    }

    #[test]
    fn notes_updated() {
        let mut col = Collection::new();
        let nt = col.basic_notetype();
        let notes: Vec<_> = (0..10)
            .map(|_| col.new_note(nt.id.into()).unwrap())
            .collect();
        let add_request = AddNotesRequest {
            requests: notes
                .iter()
                .cloned()
                .map(|note| AddNoteRequest {
                    note: Some(note),
                    deck_id: 1,
                })
                .collect(),
        };
        let _ = NotesService::add_notes(&mut col, add_request).unwrap();
        let notes: Vec<anki_proto::notes::Note> = col
            .get_all_notes()
            .into_iter()
            .map(|mut note| {
                note.fields[0] = "foo".into();
                note.into()
            })
            .collect();

        // No undo
        let request = UpdateNotesRequest {
            notes: notes.clone(),
            skip_undo_entry: true,
        };
        let _ = NotesService::update_notes(&mut col, request).unwrap();
        assert_eq!(col.can_undo(), None);

        // With undo
        let notes: Vec<anki_proto::notes::Note> = col
            .get_all_notes()
            .into_iter()
            .map(|mut note| {
                note.fields[0] = "bar".into();
                note.into()
            })
            .collect();
        let request = UpdateNotesRequest {
            notes,
            skip_undo_entry: false,
        };
        let _ = NotesService::update_notes(&mut col, request).unwrap();
        assert_ne!(col.can_undo(), None);
    }

    #[test]
    fn get_note() {
        let mut col = Collection::new();
        let nt = col.basic_notetype();
        let note1 = col.new_note(nt.id.into()).unwrap();
        let response = NotesService::add_note(
            &mut col,
            AddNoteRequest {
                note: Some(note1),
                deck_id: 1,
            },
        )
        .unwrap();
        let nid = response.note_id;
        let note2 = NotesService::get_note(&mut col, anki_proto::notes::NoteId { nid }).unwrap();
        assert_eq!(nid, note2.id);

        assert_matches!(
            NotesService::get_note(&mut col, anki_proto::notes::NoteId { nid: 0 }),
            Err(AnkiError::NotFound { .. })
        );
    }

    #[test]
    fn remove_notes() {
        let mut col = Collection::new();
        let nt = col.basic_notetype();
        let notes: Vec<_> = (0..10)
            .map(|_| col.new_note(nt.id.into()).unwrap())
            .collect();
        let add_request = AddNotesRequest {
            requests: notes
                .iter()
                .cloned()
                .map(|note| AddNoteRequest {
                    note: Some(note),
                    deck_id: 1,
                })
                .collect(),
        };
        let add_response = NotesService::add_notes(&mut col, add_request).unwrap();
        let (note_ids, remaining_nids) = add_response.nids.split_at(3);
        let note_ids: Vec<_> = note_ids.into();
        let card_ids: Vec<_> = remaining_nids
            .iter()
            .copied()
            .flat_map(|nid| {
                col.cards_of_note(anki_proto::notes::NoteId { nid })
                    .unwrap()
                    .cids
            })
            .collect();
        let response = NotesService::remove_notes(
            &mut col,
            RemoveNotesRequest {
                // note_ids takes precedence
                note_ids: note_ids.clone(),
                // An invalid ID to confirm card_ids is unused when note_ids is set
                card_ids: [0].into(),
            },
        )
        .unwrap();
        assert_eq!(response.count as usize, note_ids.len());
        assert_ne!(response.changes, None);
        let response = NotesService::remove_notes(
            &mut col,
            RemoveNotesRequest {
                note_ids: vec![],
                card_ids: card_ids.clone(),
            },
        )
        .unwrap();
        assert_eq!(response.count as usize, card_ids.len());
        assert_ne!(response.changes, None);

        assert_eq!(col.get_all_notes().len(), 0);
    }

    #[test]
    fn cloze_numbers_in_note() {
        let mut col: Collection = Collection::new();
        let nt = col.cloze_notetype();
        let mut note = col.new_note(nt.id.into()).unwrap();
        note.fields[0] = "{{c3::single}} {{c1,2::multi}}".into();
        let _ = NotesService::add_note(
            &mut col,
            AddNoteRequest {
                note: Some(note.clone()),
                deck_id: 1,
            },
        )
        .unwrap();
        let response = NotesService::cloze_numbers_in_note(&mut col, note).unwrap();
        let expected_numbers = HashSet::from_iter([1, 2, 3]);
        let extracted_numbers: HashSet<_> = HashSet::from_iter(response.numbers);
        assert_eq!(expected_numbers, extracted_numbers);
    }

    #[test]
    fn field_names_for_notes() {
        let mut col: Collection = Collection::new();
        let nt = col.cloze_notetype();
        let note = col.new_note(nt.id.into()).unwrap();
        let response = NotesService::add_note(
            &mut col,
            AddNoteRequest {
                note: Some(note.clone()),
                deck_id: 1,
            },
        )
        .unwrap();
        let nid = response.note_id;
        let mut response = NotesService::field_names_for_notes(
            &mut col,
            FieldNamesForNotesRequest { nids: vec![nid] },
        )
        .unwrap();
        let mut notetype_fields: Vec<_> = nt.field_names().cloned().collect();
        notetype_fields.sort();
        response.fields.sort();
        assert_eq!(response.fields, notetype_fields);
    }

    #[test]
    fn note_fields_check() {
        let mut col: Collection = Collection::new();
        let nt = col.basic_notetype();
        let note = col.new_note(nt.id.into()).unwrap();
        let _ = NotesService::add_note(
            &mut col,
            AddNoteRequest {
                note: Some(note.clone()),
                deck_id: 1,
            },
        )
        .unwrap();

        let response = NotesService::note_fields_check(&mut col, note).unwrap();
        assert_eq!(
            response.state(),
            anki_proto::notes::note_fields_check_response::State::Empty
        );
    }

    #[test]
    fn cards_of_note() {
        let mut col: Collection = Collection::new();
        let nt = col.basic_rev_notetype();
        let mut note = col.new_note(nt.id.into()).unwrap();
        note.fields[0] = "f".into();
        note.fields[1] = "b".into();
        let response = NotesService::add_note(
            &mut col,
            AddNoteRequest {
                note: Some(note.clone()),
                deck_id: 1,
            },
        )
        .unwrap();
        let nid = response.note_id;

        let response =
            NotesService::cards_of_note(&mut col, anki_proto::notes::NoteId { nid }).unwrap();
        assert_eq!(response.cids.len(), 2);
    }

    #[test]
    fn get_single_notetype_of_notes() {
        let mut col: Collection = Collection::new();
        let nt = col.basic_rev_notetype();
        let mut note = col.new_note(nt.id.into()).unwrap();
        note.fields[0] = "f".into();
        note.fields[1] = "b".into();
        let response = NotesService::add_note(
            &mut col,
            AddNoteRequest {
                note: Some(note.clone()),
                deck_id: 1,
            },
        )
        .unwrap();
        let nid = response.note_id;
        let response = NotesService::get_single_notetype_of_notes(
            &mut col,
            NoteIds {
                note_ids: vec![nid],
            },
        )
        .unwrap();
        assert_eq!(response.ntid, nt.id.0);
    }
}
