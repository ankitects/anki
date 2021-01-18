// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::backend_proto::note_is_duplicate_or_empty_out::State as DuplicateState;
use crate::{
    backend_proto as pb,
    collection::Collection,
    decks::DeckID,
    define_newtype,
    err::{AnkiError, Result},
    notetype::{CardGenContext, NoteField, NoteType, NoteTypeID},
    template::field_is_empty,
    text::{ensure_string_in_nfc, normalize_to_nfc, strip_html_preserving_media_filenames},
    timestamp::TimestampSecs,
    types::Usn,
};
use itertools::Itertools;
use num_integer::Integer;
use regex::{Regex, Replacer};
use std::{
    borrow::Cow,
    collections::{HashMap, HashSet},
    convert::TryInto,
};

define_newtype!(NoteID, i64);

#[derive(Default)]
pub(crate) struct TransformNoteOutput {
    pub changed: bool,
    pub generate_cards: bool,
    pub mark_modified: bool,
}

#[derive(Debug, PartialEq)]
pub struct Note {
    pub id: NoteID,
    pub guid: String,
    pub notetype_id: NoteTypeID,
    pub mtime: TimestampSecs,
    pub usn: Usn,
    pub tags: Vec<String>,
    pub(crate) fields: Vec<String>,
    pub(crate) sort_field: Option<String>,
    pub(crate) checksum: Option<u32>,
}

impl Note {
    pub(crate) fn new(notetype: &NoteType) -> Self {
        Note {
            id: NoteID(0),
            guid: guid(),
            notetype_id: notetype.id,
            mtime: TimestampSecs(0),
            usn: Usn(0),
            tags: vec![],
            fields: vec!["".to_string(); notetype.fields.len()],
            sort_field: None,
            checksum: None,
        }
    }

    pub fn fields(&self) -> &Vec<String> {
        &self.fields
    }

    pub fn set_field(&mut self, idx: usize, text: impl Into<String>) -> Result<()> {
        if idx >= self.fields.len() {
            return Err(AnkiError::invalid_input(
                "field idx out of range".to_string(),
            ));
        }

        self.fields[idx] = text.into();

        Ok(())
    }

    /// Prepare note for saving to the database. Does not mark it as modified.
    pub fn prepare_for_update(&mut self, nt: &NoteType, normalize_text: bool) -> Result<()> {
        assert!(nt.id == self.notetype_id);
        let notetype_field_count = nt.fields.len().max(1);
        if notetype_field_count != self.fields.len() {
            return Err(AnkiError::invalid_input(format!(
                "note has {} fields, expected {}",
                self.fields.len(),
                notetype_field_count
            )));
        }

        for field in &mut self.fields {
            if field.contains(invalid_char_for_field) {
                *field = field.replace(invalid_char_for_field, "");
            }
        }

        if normalize_text {
            for field in &mut self.fields {
                ensure_string_in_nfc(field);
            }
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

    pub(crate) fn set_modified(&mut self, usn: Usn) {
        self.mtime = TimestampSecs::now();
        self.usn = usn;
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

    pub(crate) fn replace_tags<T: Replacer>(&mut self, re: &Regex, mut repl: T) -> bool {
        let mut changed = false;
        for tag in &mut self.tags {
            if let Cow::Owned(rep) = re.replace_all(tag, |caps: &regex::Captures| {
                if let Some(expanded) = repl.by_ref().no_expansion() {
                    if expanded.trim().is_empty() {
                        "".to_string()
                    } else {
                        // include "::" if it was matched
                        format!(
                            "{}{}",
                            expanded,
                            caps.get(caps.len() - 1).map_or("", |m| m.as_str())
                        )
                    }
                } else {
                    tag.to_string()
                }
            }) {
                *tag = rep;
                changed = true;
            }
        }
        changed
    }

    /// Pad or merge fields to match note type.
    pub(crate) fn fix_field_count(&mut self, nt: &NoteType) {
        while self.fields.len() < nt.fields.len() {
            self.fields.push("".into())
        }
        while self.fields.len() > nt.fields.len() && self.fields.len() > 1 {
            let last = self.fields.pop().unwrap();
            self.fields
                .last_mut()
                .unwrap()
                .push_str(&format!("; {}", last));
        }
    }
}

impl From<Note> for pb::Note {
    fn from(n: Note) -> Self {
        pb::Note {
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

impl From<pb::Note> for Note {
    fn from(n: pb::Note) -> Self {
        Note {
            id: NoteID(n.id),
            guid: n.guid,
            notetype_id: NoteTypeID(n.notetype_id),
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
    let digest = sha1::Sha1::from(text).digest().bytes();
    u32::from_be_bytes(digest[..4].try_into().unwrap())
}

pub(crate) fn guid() -> String {
    anki_base91(rand::random())
}

fn anki_base91(mut n: u64) -> String {
    let table = b"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ\
0123456789!#$%&()*+,-./:;<=>?@[]^_`{|}~";
    let mut buf = String::new();
    while n > 0 {
        let (q, r) = n.div_rem(&(table.len() as u64));
        buf.push(table[r as usize] as char);
        n = q;
    }

    buf.chars().rev().collect()
}

fn invalid_char_for_field(c: char) -> bool {
    c.is_ascii_control() && c != '\n' && c != '\t'
}

impl Collection {
    fn canonify_note_tags(&self, note: &mut Note, usn: Usn) -> Result<()> {
        if !note.tags.is_empty() {
            let tags = std::mem::replace(&mut note.tags, vec![]);
            note.tags = self.canonify_tags(tags, usn)?.0;
        }
        Ok(())
    }

    pub fn add_note(&mut self, note: &mut Note, did: DeckID) -> Result<()> {
        self.transact(None, |col| {
            let nt = col
                .get_notetype(note.notetype_id)?
                .ok_or_else(|| AnkiError::invalid_input("missing note type"))?;
            let ctx = CardGenContext::new(&nt, col.usn()?);
            let norm = col.normalize_note_text();
            col.add_note_inner(&ctx, note, did, norm)
        })
    }

    pub(crate) fn add_note_inner(
        &mut self,
        ctx: &CardGenContext,
        note: &mut Note,
        did: DeckID,
        normalize_text: bool,
    ) -> Result<()> {
        self.canonify_note_tags(note, ctx.usn)?;
        note.prepare_for_update(&ctx.notetype, normalize_text)?;
        note.set_modified(ctx.usn);
        self.storage.add_note(note)?;
        self.generate_cards_for_new_note(ctx, note, did)
    }

    pub fn update_note(&mut self, note: &mut Note) -> Result<()> {
        if let Some(existing_note) = self.storage.get_note(note.id)? {
            if &existing_note == note {
                // nothing to do
                return Ok(());
            }
        } else {
            return Err(AnkiError::NotFound);
        }

        self.transact(None, |col| {
            let nt = col
                .get_notetype(note.notetype_id)?
                .ok_or_else(|| AnkiError::invalid_input("missing note type"))?;
            let ctx = CardGenContext::new(&nt, col.usn()?);
            let norm = col.normalize_note_text();
            col.update_note_inner_generating_cards(&ctx, note, true, norm)
        })
    }

    pub(crate) fn update_note_inner_generating_cards(
        &mut self,
        ctx: &CardGenContext,
        note: &mut Note,
        mark_note_modified: bool,
        normalize_text: bool,
    ) -> Result<()> {
        self.update_note_inner_without_cards(
            note,
            ctx.notetype,
            ctx.usn,
            mark_note_modified,
            normalize_text,
        )?;
        self.generate_cards_for_existing_note(ctx, note)
    }

    pub(crate) fn update_note_inner_without_cards(
        &mut self,
        note: &mut Note,
        nt: &NoteType,
        usn: Usn,
        mark_note_modified: bool,
        normalize_text: bool,
    ) -> Result<()> {
        self.canonify_note_tags(note, usn)?;
        note.prepare_for_update(nt, normalize_text)?;
        if mark_note_modified {
            note.set_modified(usn);
        }
        self.storage.update_note(note)
    }

    /// Remove a note. Cards must already have been deleted.
    pub(crate) fn remove_note_only(&mut self, nid: NoteID, usn: Usn) -> Result<()> {
        if let Some(_note) = self.storage.get_note(nid)? {
            // fixme: undo
            self.storage.remove_note(nid)?;
            self.storage.add_note_grave(nid, usn)?;
        }
        Ok(())
    }

    /// Remove provided notes, and any cards that use them.
    pub(crate) fn remove_notes(&mut self, nids: &[NoteID]) -> Result<()> {
        let usn = self.usn()?;
        self.transact(None, |col| {
            for nid in nids {
                let nid = *nid;
                if let Some(_existing_note) = col.storage.get_note(nid)? {
                    // fixme: undo
                    for card in col.storage.all_cards_of_note(nid)? {
                        col.remove_card_only(card, usn)?;
                    }
                    col.remove_note_only(nid, usn)?;
                }
            }

            Ok(())
        })
    }

    /// Update cards and field cache after notes modified externally.
    /// If gencards is false, skip card generation.
    pub(crate) fn after_note_updates(
        &mut self,
        nids: &[NoteID],
        generate_cards: bool,
        mark_notes_modified: bool,
    ) -> Result<()> {
        self.transform_notes(nids, |_note, _nt| {
            Ok(TransformNoteOutput {
                changed: true,
                generate_cards,
                mark_modified: mark_notes_modified,
            })
        })
        .map(|_| ())
    }

    pub(crate) fn transform_notes<F>(
        &mut self,
        nids: &[NoteID],
        mut transformer: F,
    ) -> Result<usize>
    where
        F: FnMut(&mut Note, &NoteType) -> Result<TransformNoteOutput>,
    {
        let nids_by_notetype = self.storage.note_ids_by_notetype(nids)?;
        let norm = self.normalize_note_text();
        let mut changed_notes = 0;
        let usn = self.usn()?;

        for (ntid, group) in &nids_by_notetype.into_iter().group_by(|tup| tup.0) {
            let nt = self
                .get_notetype(ntid)?
                .ok_or_else(|| AnkiError::invalid_input("missing note type"))?;

            let mut genctx = None;
            for (_, nid) in group {
                // grab the note and transform it
                let mut note = self.storage.get_note(nid)?.unwrap();
                let out = transformer(&mut note, &nt)?;
                if !out.changed {
                    continue;
                }

                if out.generate_cards {
                    let ctx = genctx.get_or_insert_with(|| CardGenContext::new(&nt, usn));
                    self.update_note_inner_generating_cards(
                        &ctx,
                        &mut note,
                        out.mark_modified,
                        norm,
                    )?;
                } else {
                    self.update_note_inner_without_cards(
                        &mut note,
                        &nt,
                        usn,
                        out.mark_modified,
                        norm,
                    )?;
                }

                changed_notes += 1;
            }
        }

        Ok(changed_notes)
    }

    pub(crate) fn note_is_duplicate_or_empty(&self, note: &Note) -> Result<DuplicateState> {
        if let Some(field1) = note.fields.get(0) {
            let field1 = if self.normalize_note_text() {
                normalize_to_nfc(field1)
            } else {
                field1.into()
            };
            let stripped = strip_html_preserving_media_filenames(&field1);
            if stripped.trim().is_empty() {
                Ok(DuplicateState::Empty)
            } else {
                let csum = field_checksum(&stripped);
                let have_dupe = self
                    .storage
                    .note_fields_by_checksum(note.notetype_id, csum)?
                    .into_iter()
                    .any(|(nid, field)| {
                        nid != note.id && strip_html_preserving_media_filenames(&field) == stripped
                    });

                if have_dupe {
                    Ok(DuplicateState::Duplicate)
                } else {
                    Ok(DuplicateState::Normal)
                }
            }
        } else {
            Ok(DuplicateState::Empty)
        }
    }
}

#[cfg(test)]
mod test {
    use super::{anki_base91, field_checksum};
    use crate::{
        collection::open_test_collection, config::ConfigKey, decks::DeckID, err::Result,
        search::SortMode,
    };

    #[test]
    fn test_base91() {
        // match the python implementation for now
        assert_eq!(anki_base91(0), "");
        assert_eq!(anki_base91(1), "b");
        assert_eq!(anki_base91(u64::max_value()), "Rj&Z5m[>Zp");
        assert_eq!(anki_base91(1234567890), "saAKk");
    }

    #[test]
    fn test_field_checksum() {
        assert_eq!(field_checksum("test"), 2840236005);
        assert_eq!(field_checksum("今日"), 1464653051);
    }

    #[test]
    fn adding_cards() -> Result<()> {
        let mut col = open_test_collection();
        let nt = col
            .get_notetype_by_name("basic (and reversed card)")?
            .unwrap();

        let mut note = nt.new_note();
        // if no cards are generated, 1 card is added
        col.add_note(&mut note, DeckID(1)).unwrap();
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
        col.add_note(&mut note, DeckID(1)).unwrap();
        let existing = col.storage.existing_cards_for_note(note.id)?;
        assert_eq!(existing.len(), 1);
        assert_eq!(existing[0].ord, 0);
        assert_eq!(existing[0].original_deck_id, DeckID(1));

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
        let mut col = open_test_collection();

        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        note.fields[0] = "\u{fa47}".into();
        col.add_note(&mut note, DeckID(1))?;
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
        col.set_config(ConfigKey::NormalizeNoteText, &false)
            .unwrap();
        col.add_note(&mut note, DeckID(1))?;
        assert_eq!(note.fields[0], "\u{fa47}");
        // normalized searches won't match
        assert_eq!(col.search_cards("\u{6f22}", SortMode::NoOrder)?.len(), 0);
        // but original characters will
        assert_eq!(col.search_cards("\u{fa47}", SortMode::NoOrder)?.len(), 1);

        Ok(())
    }
}
