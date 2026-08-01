// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub(crate) mod service;
pub(crate) mod undo;

use std::borrow::Cow;
use std::collections::HashMap;
use std::collections::HashSet;

use anki_proto::notes::note_fields_check_response::State as NoteFieldsState;
use itertools::Itertools;
use sha1::Digest;
use sha1::Sha1;

use crate::cloze::contains_cloze;
use crate::define_newtype;
use crate::error;
use crate::error::AnkiError;
use crate::error::OrInvalid;
use crate::notetype::CardGenContext;
use crate::notetype::NoteField;
use crate::ops::StateChanges;
use crate::prelude::*;
use crate::template::field_is_empty;
use crate::text::ensure_string_in_nfc;
use crate::text::normalize_to_nfc;
use crate::text::strip_html_preserving_media_filenames;

define_newtype!(NoteId, i64);

#[derive(Default)]
pub(crate) struct TransformNoteOutput {
    pub changed: bool,
    pub generate_cards: bool,
    pub mark_modified: bool,
    pub update_tags: bool,
}

#[derive(Debug, PartialEq, Eq, Clone)]
pub struct Note {
    pub id: NoteId,
    pub guid: String,
    pub notetype_id: NotetypeId,
    pub mtime: TimestampSecs,
    pub usn: Usn,
    pub tags: Vec<String>,
    fields: Vec<String>,
    pub(crate) sort_field: Option<String>,
    pub(crate) checksum: Option<u32>,
}

impl Note {
    pub fn fields(&self) -> &Vec<String> {
        &self.fields
    }

    pub fn into_fields(self) -> Vec<String> {
        self.fields
    }

    pub fn set_field(&mut self, idx: usize, text: impl Into<String>) -> Result<()> {
        require!(idx < self.fields.len(), "field idx out of range");

        self.fields[idx] = text.into();
        self.mark_dirty();

        Ok(())
    }
}

#[derive(Debug, Clone)]
pub struct AddNoteRequest {
    pub note: Note,
    pub deck_id: DeckId,
}

impl TryFrom<anki_proto::notes::AddNoteRequest> for AddNoteRequest {
    type Error = AnkiError;

    fn try_from(request: anki_proto::notes::AddNoteRequest) -> error::Result<Self, Self::Error> {
        Ok(Self {
            note: request.note.or_invalid("no note provided")?.into(),
            deck_id: DeckId(request.deck_id),
        })
    }
}

impl Collection {
    pub fn add_note(&mut self, note: &mut Note, did: DeckId) -> Result<OpOutput<usize>> {
        self.transact(Op::AddNote, |col| col.add_note_inner(note, did))
    }

    pub fn add_notes(&mut self, requests: &mut [AddNoteRequest]) -> Result<OpOutput<()>> {
        self.transact(Op::AddNote, |col| {
            for request in requests {
                col.add_note_inner(&mut request.note, request.deck_id)?;
            }

            Ok(())
        })
    }

    /// Remove provided notes, and any cards that use them.
    pub fn remove_notes(&mut self, nids: &[NoteId]) -> Result<OpOutput<usize>> {
        let usn = self.usn()?;
        self.transact(Op::RemoveNote, |col| col.remove_notes_inner(nids, usn))
    }

    /// Update cards and field cache after notes modified externally.
    /// If gencards is false, skip card generation.
    pub fn after_note_updates(
        &mut self,
        nids: &[NoteId],
        generate_cards: bool,
        mark_notes_modified: bool,
    ) -> Result<OpOutput<usize>> {
        self.transact(Op::UpdateNote, |col| {
            col.after_note_updates_inner(nids, generate_cards, mark_notes_modified)
        })
    }
}

/// Information required for updating tags while leaving note content alone.
/// Tags are stored in their DB form, separated by spaces.
#[derive(Debug, PartialEq, Eq, Clone)]
pub(crate) struct NoteTags {
    pub id: NoteId,
    pub mtime: TimestampSecs,
    pub usn: Usn,
    pub tags: String,
}

impl NoteTags {
    pub(crate) fn set_modified(&mut self, usn: Usn) {
        self.mtime = TimestampSecs::now();
        self.usn = usn;
    }
}

impl Note {
    pub fn new(notetype: &Notetype) -> Self {
        Note {
            id: NoteId(0),
            guid: base91_u64(),
            notetype_id: notetype.id,
            mtime: TimestampSecs(0),
            usn: Usn(0),
            tags: vec![],
            fields: vec!["".to_string(); notetype.fields.len()],
            sort_field: None,
            checksum: None,
        }
    }

    #[allow(clippy::too_many_arguments)]
    pub(crate) fn new_from_storage(
        id: NoteId,
        guid: String,
        notetype_id: NotetypeId,
        mtime: TimestampSecs,
        usn: Usn,
        tags: Vec<String>,
        fields: Vec<String>,
        sort_field: Option<String>,
        checksum: Option<u32>,
    ) -> Self {
        Self {
            id,
            guid,
            notetype_id,
            mtime,
            usn,
            tags,
            fields,
            sort_field,
            checksum,
        }
    }

    pub fn fields_mut(&mut self) -> &mut Vec<String> {
        self.mark_dirty();
        &mut self.fields
    }

    // Ensure we get an error if caller forgets to call prepare_for_update().
    fn mark_dirty(&mut self) {
        self.sort_field = None;
        self.checksum = None;
    }

    /// Prepare note for saving to the database. Does not mark it as modified.
    pub(crate) fn prepare_for_update(&mut self, nt: &Notetype, normalize_text: bool) -> Result<()> {
        assert_eq!(nt.id, self.notetype_id);
        let notetype_field_count = nt.fields.len().max(1);
        require!(
            notetype_field_count == self.fields.len(),
            "note has {} fields, expected {notetype_field_count}",
            self.fields.len()
        );

        for field in self.fields_mut() {
            normalize_field(field, normalize_text);
        }

        let field1_nohtml = strip_html_preserving_media_filenames(&self.fields()[0]);
        let checksum = field_checksum(field1_nohtml.as_ref());
        let sort_field = if nt.config.sort_field_idx == 0 {
            field1_nohtml
        } else {
            strip_html_preserving_media_filenames(
                self.fields
                    .get(nt.config.sort_field_idx as usize)
                    .map(AsRef::as_ref)
                    .unwrap_or(""),
            )
        };
        self.sort_field = Some(sort_field.into());
        self.checksum = Some(checksum);
        Ok(())
    }

    #[inline]
    pub(crate) fn set_modified_with_mtime(&mut self, usn: Usn, mtime: TimestampSecs) {
        self.mtime = mtime;
        self.usn = usn;
    }

    pub(crate) fn set_modified(&mut self, usn: Usn) {
        self.set_modified_with_mtime(usn, TimestampSecs::now())
    }

    pub(crate) fn nonempty_fields<'a>(&self, fields: &'a [NoteField]) -> HashSet<&'a str> {
        self.fields
            .iter()
            .enumerate()
            .filter_map(|(ord, s)| {
                if field_is_empty(s) {
                    None
                } else {
                    fields.get(ord).map(|f| f.name.as_str())
                }
            })
            .collect()
    }

    pub(crate) fn fields_map<'a>(
        &'a self,
        fields: &'a [NoteField],
    ) -> HashMap<&'a str, Cow<'a, str>> {
        self.fields
            .iter()
            .enumerate()
            .map(|(ord, field_content)| {
                (
                    fields.get(ord).map(|f| f.name.as_str()).unwrap_or(""),
                    field_content.as_str().into(),
                )
            })
            .collect()
    }

    /// Pad or merge fields to match note type.
    pub(crate) fn fix_field_count(&mut self, nt: &Notetype) {
        while self.fields.len() < nt.fields.len() {
            self.fields.push("".into())
        }
        while self.fields.len() > nt.fields.len() && self.fields.len() > 1 {
            let last = self.fields.pop().unwrap();
            self.fields
                .last_mut()
                .unwrap()
                .push_str(&format!("; {last}"));
        }
    }
}

/// Remove invalid characters and optionally ensure nfc normalization.
pub(crate) fn normalize_field(field: &mut String, normalize_text: bool) {
    if field.contains(invalid_char_for_field) {
        *field = field.replace(invalid_char_for_field, "");
    }
    if normalize_text {
        ensure_string_in_nfc(field);
    }
}

impl From<Note> for anki_proto::notes::Note {
    fn from(n: Note) -> Self {
        anki_proto::notes::Note {
            id: n.id.0,
            guid: n.guid,
            notetype_id: n.notetype_id.0,
            mtime_secs: n.mtime.0 as u32,
            usn: n.usn.0,
            tags: n.tags,
            fields: n.fields,
        }
    }
}

impl From<anki_proto::notes::Note> for Note {
    fn from(n: anki_proto::notes::Note) -> Self {
        Note {
            id: NoteId(n.id),
            guid: n.guid,
            notetype_id: NotetypeId(n.notetype_id),
            mtime: TimestampSecs(n.mtime_secs as i64),
            usn: Usn(n.usn),
            tags: n.tags,
            fields: n.fields,
            sort_field: None,
            checksum: None,
        }
    }
}

/// Text must be passed to strip_html_preserving_media_filenames() by
/// caller prior to passing in here.
pub(crate) fn field_checksum(text: &str) -> u32 {
    let mut hash = Sha1::new();
    hash.update(text);
    let digest = hash.finalize();
    u32::from_be_bytes(digest[..4].try_into().unwrap())
}

pub(crate) fn base91_u64() -> String {
    anki_base91(rand::random())
}

fn anki_base91(n: u64) -> String {
    to_base_n(
        n,
        b"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ\
0123456789!#$%&()*+,-./:;<=>?@[]^_`{|}~",
    )
}

pub fn to_base_n(mut n: u64, table: &[u8]) -> String {
    let mut buf = String::new();
    while n > 0 {
        let tablelen = table.len() as u64;
        let (q, r) = (n / tablelen, n % tablelen);
        buf.push(table[r as usize] as char);
        n = q;
    }
    buf.chars().rev().collect()
}

fn invalid_char_for_field(c: char) -> bool {
    c.is_ascii_control() && c != '\n' && c != '\t'
}

/// Used when calling [Collection::update_note_inner_without_cards] and
/// [Collection::update_note_inner_without_cards_using_mtime]
pub(crate) struct UpdateNoteInnerWithoutCardsArgs<'a> {
    pub(crate) note: &'a mut Note,
    pub(crate) original: &'a Note,
    pub(crate) notetype: &'a Notetype,
    pub(crate) usn: Usn,
    pub(crate) mark_note_modified: bool,
    pub(crate) normalize_text: bool,
    pub(crate) update_tags: bool,
}

impl Collection {
    pub(crate) fn canonify_note_tags(&mut self, note: &mut Note, usn: Usn) -> Result<()> {
        if !note.tags.is_empty() {
            let tags = std::mem::take(&mut note.tags);
            note.tags = self.canonify_tags(tags, usn)?.0;
        }
        Ok(())
    }

    pub(crate) fn add_note_inner(&mut self, note: &mut Note, did: DeckId) -> Result<usize> {
        let nt = self
            .get_notetype(note.notetype_id)?
            .or_invalid("missing note type")?;
        let last_deck = self.get_last_deck_added_to_for_notetype(note.notetype_id);
        let ctx = CardGenContext::new(nt.as_ref(), last_deck, self.usn()?);
        let normalize_text = self.get_config_bool(BoolKey::NormalizeNoteText);
        self.canonify_note_tags(note, ctx.usn)?;
        note.prepare_for_update(ctx.notetype, normalize_text)?;
        note.set_modified(ctx.usn);
        self.add_note_only_undoable(note)?;
        let count = self.generate_cards_for_new_note(&ctx, note, did)?;
        self.set_last_deck_for_notetype(note.notetype_id, did)?;
        self.set_last_notetype_for_deck(did, note.notetype_id)?;
        self.set_current_notetype_id(note.notetype_id)?;
        Ok(count)
    }

    pub fn update_note(&mut self, note: &mut Note) -> Result<OpOutput<()>> {
        self.transact(Op::UpdateNote, |col| col.update_note_inner(note))
    }

    pub(crate) fn update_notes_maybe_undoable(
        &mut self,
        notes: Vec<Note>,
        undoable: bool,
    ) -> Result<OpOutput<()>> {
        if undoable {
            self.transact(Op::UpdateNote, |col| {
                for mut note in notes {
                    col.update_note_inner(&mut note)?;
                }
                Ok(())
            })
        } else {
            self.transact_no_undo(|col| {
                for mut note in notes {
                    col.update_note_inner(&mut note)?;
                }
                Ok(OpOutput {
                    output: (),
                    changes: OpChanges {
                        op: Op::UpdateNote,
                        changes: StateChanges {
                            note: true,
                            tag: true,
                            card: true,
                            ..Default::default()
                        },
                    },
                })
            })
        }
    }

    pub(crate) fn update_note_inner(&mut self, note: &mut Note) -> Result<()> {
        let mut existing_note = self.storage.get_note(note.id)?.or_not_found(note.id)?;
        if !note_differs_from_db(&mut existing_note, note) {
            // nothing to do
            return Ok(());
        }
        let nt = self
            .get_notetype(note.notetype_id)?
            .or_invalid("missing note type")?;
        let last_deck = self.get_last_deck_added_to_for_notetype(note.notetype_id);
        let ctx = CardGenContext::new(nt.as_ref(), last_deck, self.usn()?);
        let norm = self.get_config_bool(BoolKey::NormalizeNoteText);
        self.update_note_inner_generating_cards(&ctx, note, &existing_note, true, norm, true)?;
        Ok(())
    }

    pub(crate) fn update_note_inner_generating_cards(
        &mut self,
        ctx: &CardGenContext<&Notetype>,
        note: &mut Note,
        original: &Note,
        mark_note_modified: bool,
        normalize_text: bool,
        update_tags: bool,
    ) -> Result<()> {
        self.update_note_inner_without_cards(UpdateNoteInnerWithoutCardsArgs {
            note,
            original,
            notetype: ctx.notetype,
            usn: ctx.usn,
            mark_note_modified,
            normalize_text,
            update_tags,
        })?;
        self.generate_cards_for_existing_note(ctx, note)
    }

    #[inline]
    pub(crate) fn update_note_inner_without_cards_using_mtime(
        &mut self,
        UpdateNoteInnerWithoutCardsArgs {
            note,
            original,
            notetype,
            usn,
            mark_note_modified,
            normalize_text,
            update_tags,
        }: UpdateNoteInnerWithoutCardsArgs,
        mtime: Option<TimestampSecs>,
    ) -> Result<()> {
        if update_tags {
            self.canonify_note_tags(note, usn)?;
        }
        note.prepare_for_update(notetype, normalize_text)?;
        if mark_note_modified {
            if let Some(mtime) = mtime {
                note.set_modified_with_mtime(usn, mtime);
            } else {
                note.set_modified(usn);
            }
        }
        self.update_note_undoable(note, original)
    }

    pub(crate) fn update_note_inner_without_cards(
        &mut self,
        args: UpdateNoteInnerWithoutCardsArgs<'_>,
    ) -> Result<()> {
        self.update_note_inner_without_cards_using_mtime(args, None)
    }

    pub(crate) fn remove_notes_inner(&mut self, nids: &[NoteId], usn: Usn) -> Result<usize> {
        let mut card_count = 0;
        for nid in nids {
            let nid = *nid;
            if let Some(_existing_note) = self.storage.get_note(nid)? {
                for card in self.storage.all_cards_of_note(nid)? {
                    card_count += 1;
                    self.remove_card_and_add_grave_undoable(card, usn)?;
                }
                self.remove_note_only_undoable(nid, usn)?;
            }
        }
        Ok(card_count)
    }

    fn after_note_updates_inner(
        &mut self,
        nids: &[NoteId],
        generate_cards: bool,
        mark_notes_modified: bool,
    ) -> Result<usize> {
        self.transform_notes(nids, |_note, _nt| {
            Ok(TransformNoteOutput {
                changed: true,
                generate_cards,
                mark_modified: mark_notes_modified,
                update_tags: true,
            })
        })
    }

    pub(crate) fn transform_notes<F>(
        &mut self,
        nids: &[NoteId],
        mut transformer: F,
    ) -> Result<usize>
    where
        F: FnMut(&mut Note, &Notetype) -> Result<TransformNoteOutput>,
    {
        let nids_by_notetype = self.storage.note_ids_by_notetype(nids)?;
        let norm = self.get_config_bool(BoolKey::NormalizeNoteText);
        let mut changed_notes = 0;
        let usn = self.usn()?;

        for (ntid, group) in &nids_by_notetype.into_iter().chunk_by(|tup| tup.0) {
            let nt = self.get_notetype(ntid)?.or_invalid("missing note type")?;

            let mut genctx = None;
            for (_, nid) in group {
                // grab the note and transform it
                let mut note = self.storage.get_note(nid)?.unwrap();
                let original = note.clone();
                let out = transformer(&mut note, &nt)?;
                if !out.changed {
                    continue;
                }

                if out.generate_cards {
                    let ctx = genctx.get_or_insert_with(|| {
                        CardGenContext::new(
                            nt.as_ref(),
                            self.get_last_deck_added_to_for_notetype(nt.id),
                            usn,
                        )
                    });
                    self.update_note_inner_generating_cards(
                        ctx,
                        &mut note,
                        &original,
                        out.mark_modified,
                        norm,
                        out.update_tags,
                    )?;
                } else {
                    self.update_note_inner_without_cards(UpdateNoteInnerWithoutCardsArgs {
                        note: &mut note,
                        original: &original,
                        notetype: &nt,
                        usn,
                        mark_note_modified: out.mark_modified,
                        normalize_text: norm,
                        update_tags: out.update_tags,
                    })?;
                }

                changed_notes += 1;
            }
        }

        Ok(changed_notes)
    }

    /// Check if there is a cloze in a non-cloze field. Then check if the
    /// note's first field is empty. For cloze notetypes, check whether there
    /// is a cloze at all. Finally, check if the first field is a duplicate.
    pub fn note_fields_check(&mut self, note: &Note) -> Result<NoteFieldsState> {
        Ok({
            let cloze_state = self.field_cloze_check(note)?;
            if cloze_state == NoteFieldsState::FieldNotCloze {
                NoteFieldsState::FieldNotCloze
            } else if let Some(text) = note.fields.first() {
                let field1 = if self.get_config_bool(BoolKey::NormalizeNoteText) {
                    normalize_to_nfc(text)
                } else {
                    text.into()
                };
                let stripped = strip_html_preserving_media_filenames(&field1);
                if stripped.trim().is_empty() {
                    NoteFieldsState::Empty
                } else if cloze_state != NoteFieldsState::Normal {
                    cloze_state
                } else if self.is_duplicate(&stripped, note)? {
                    NoteFieldsState::Duplicate
                } else {
                    NoteFieldsState::Normal
                }
            } else {
                NoteFieldsState::Empty
            }
        })
    }

    fn is_duplicate(&self, first_field: &str, note: &Note) -> Result<bool> {
        let csum = field_checksum(first_field);
        Ok(self
            .storage
            .note_fields_by_checksum(note.notetype_id, csum)?
            .into_iter()
            .any(|(nid, field)| {
                nid != note.id && strip_html_preserving_media_filenames(&field) == first_field
            }))
    }

    fn field_cloze_check(&mut self, note: &Note) -> Result<NoteFieldsState> {
        let notetype = self
            .get_notetype(note.notetype_id)?
            .or_not_found(note.notetype_id)?;
        let cloze_fields = notetype.cloze_fields();
        let mut has_cloze = false;
        let extraneous_cloze = note.fields.iter().enumerate().find_map(|(i, field)| {
            if notetype.is_cloze() {
                if contains_cloze(field) {
                    if cloze_fields.contains(&i) {
                        has_cloze = true;
                        None
                    } else {
                        Some(NoteFieldsState::FieldNotCloze)
                    }
                } else {
                    None
                }
            } else if contains_cloze(field) {
                Some(NoteFieldsState::NotetypeNotCloze)
            } else {
                None
            }
        });
        Ok(if let Some(state) = extraneous_cloze {
            state
        } else if notetype.is_cloze() && !has_cloze {
            NoteFieldsState::MissingCloze
        } else {
            NoteFieldsState::Normal
        })
    }
}

/// The existing note pulled from the DB will have sfld and csum set, but the
/// note we receive from the frontend won't. Temporarily zero them out and
/// compare, then restore them again.
/// Also set mtime to existing, since the frontend may have a stale mtime, and
/// we'll bump it as we save in any case.
fn note_differs_from_db(existing_note: &mut Note, note: &mut Note) -> bool {
    let sort_field = existing_note.sort_field.take();
    let checksum = existing_note.checksum.take();
    note.mtime = existing_note.mtime;
    let notes_differ = existing_note != note;
    existing_note.sort_field = sort_field;
    existing_note.checksum = checksum;
    notes_differ
}

#[cfg(test)]
mod test {
    use super::anki_base91;
    use super::field_checksum;
    use crate::config::BoolKey;
    use crate::decks::DeckId;
    use crate::error::Result;
    use crate::prelude::*;
    use crate::search::SortMode;

    #[test]
    fn test_base91() {
        // match the python implementation for now
        assert_eq!(anki_base91(0), "");
        assert_eq!(anki_base91(1), "b");
        assert_eq!(anki_base91(u64::MAX), "Rj&Z5m[>Zp");
        assert_eq!(anki_base91(1234567890), "saAKk");
    }

    #[test]
    fn test_field_checksum() {
        assert_eq!(field_checksum("test"), 2840236005);
        assert_eq!(field_checksum("今日"), 1464653051);
    }

    #[test]
    fn adding_cards() -> Result<()> {
        let mut col = Collection::new();
        let nt = col
            .get_notetype_by_name("basic (and reversed card)")?
            .unwrap();

        let mut note = nt.new_note();
        // if no cards are generated, 1 card is added
        col.add_note(&mut note, DeckId(1)).unwrap();
        let existing = col.storage.existing_cards_for_note(note.id)?;
        assert_eq!(existing.len(), 1);
        assert_eq!(existing[0].ord, 0);

        // nothing changes if the first field is filled
        note.fields[0] = "test".into();
        col.update_note(&mut note).unwrap();
        let existing = col.storage.existing_cards_for_note(note.id)?;
        assert_eq!(existing.len(), 1);
        assert_eq!(existing[0].ord, 0);

        // second field causes another card to be generated
        note.fields[1] = "test".into();
        col.update_note(&mut note).unwrap();
        let existing = col.storage.existing_cards_for_note(note.id)?;
        assert_eq!(existing.len(), 2);
        assert_eq!(existing[1].ord, 1);

        // cloze cards also generate card 0 if no clozes are found
        let nt = col.get_notetype_by_name("cloze")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1)).unwrap();
        let existing = col.storage.existing_cards_for_note(note.id)?;
        assert_eq!(existing.len(), 1);
        assert_eq!(existing[0].ord, 0);
        assert_eq!(existing[0].original_deck_id, DeckId(1));

        // and generate cards for any cloze deletions
        note.fields[0] = "{{c1::foo}} {{c2::bar}} {{c3::baz}} {{c0::quux}} {{c501::over}}".into();
        col.update_note(&mut note)?;
        let existing = col.storage.existing_cards_for_note(note.id)?;
        let mut ords = existing.iter().map(|a| a.ord).collect::<Vec<_>>();
        ords.sort_unstable();
        assert_eq!(ords, vec![0, 1, 2, 499]);

        Ok(())
    }

    #[test]
    fn normalization() -> Result<()> {
        let mut col = Collection::new();

        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        note.fields[0] = "\u{fa47}".into();
        col.add_note(&mut note, DeckId(1))?;
        assert_eq!(note.fields[0], "\u{6f22}");
        // non-normalized searches should be converted
        assert_eq!(col.search_cards("\u{fa47}", SortMode::NoOrder)?.len(), 1);
        assert_eq!(
            col.search_cards("front:\u{fa47}", SortMode::NoOrder)?.len(),
            1
        );
        let cids = col.search_cards("", SortMode::NoOrder)?;
        col.remove_cards_and_orphaned_notes(&cids)?;

        // if normalization turned off, note text is entered as-is
        let mut note = nt.new_note();
        note.fields[0] = "\u{fa47}".into();
        col.set_config(BoolKey::NormalizeNoteText, &false).unwrap();
        col.add_note(&mut note, DeckId(1))?;
        assert_eq!(note.fields[0], "\u{fa47}");
        // normalized searches won't match
        assert_eq!(col.search_cards("\u{6f22}", SortMode::NoOrder)?.len(), 0);
        // but original characters will
        assert_eq!(col.search_cards("\u{fa47}", SortMode::NoOrder)?.len(), 1);

        Ok(())
    }

    #[test]
    fn undo() -> Result<()> {
        let mut col = Collection::new();
        let nt = col
            .get_notetype_by_name("basic (and reversed card)")?
            .unwrap();

        let assert_initial = |col: &mut Collection| -> Result<()> {
            assert_eq!(col.search_notes_unordered("")?.len(), 0);
            assert_eq!(col.search_cards("", SortMode::NoOrder)?.len(), 0);
            assert_eq!(
                col.storage.db_scalar::<u32>("select count() from graves")?,
                0
            );
            assert!(col.get_next_card()?.is_none());
            Ok(())
        };

        let assert_after_add = |col: &mut Collection| -> Result<()> {
            assert_eq!(col.search_notes_unordered("")?.len(), 1);
            assert_eq!(col.search_cards("", SortMode::NoOrder)?.len(), 2);
            assert_eq!(
                col.storage.db_scalar::<u32>("select count() from graves")?,
                0
            );
            assert!(col.get_next_card()?.is_some());
            Ok(())
        };

        assert_initial(&mut col)?;

        let mut note = nt.new_note();
        note.set_field(0, "a")?;
        note.set_field(1, "b")?;

        col.add_note(&mut note, DeckId(1)).unwrap();

        assert_after_add(&mut col)?;
        col.undo()?;
        assert_initial(&mut col)?;
        col.redo()?;
        assert_after_add(&mut col)?;
        col.undo()?;
        assert_initial(&mut col)?;

        let assert_after_remove = |col: &mut Collection| -> Result<()> {
            assert_eq!(col.search_notes_unordered("")?.len(), 0);
            assert_eq!(col.search_cards("", SortMode::NoOrder)?.len(), 0);
            // 1 note + 2 cards
            assert_eq!(
                col.storage.db_scalar::<u32>("select count() from graves")?,
                3
            );
            assert!(col.get_next_card()?.is_none());
            Ok(())
        };

        col.redo()?;
        assert_after_add(&mut col)?;
        let nids = col.search_notes_unordered("")?;
        col.remove_notes(&nids)?;
        assert_after_remove(&mut col)?;
        col.undo()?;
        assert_after_add(&mut col)?;
        col.redo()?;
        assert_after_remove(&mut col)?;

        Ok(())
    }
}
