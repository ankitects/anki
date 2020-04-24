// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    backend_proto as pb,
    collection::Collection,
    decks::DeckID,
    define_newtype,
    err::{AnkiError, Result},
    notetype::{CardGenContext, NoteField, NoteType, NoteTypeID},
    text::strip_html_preserving_image_filenames,
    timestamp::TimestampSecs,
    types::Usn,
};
use num_integer::Integer;
use std::{collections::HashSet, convert::TryInto};

define_newtype!(NoteID, i64);

// fixme: ensure nulls and x1f not in field contents

#[derive(Debug)]
pub struct Note {
    pub id: NoteID,
    pub guid: String,
    pub ntid: NoteTypeID,
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
            ntid: notetype.id,
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

    /// Prepare note for saving to the database. If usn is provided, mtime will be bumped.
    pub fn prepare_for_update(&mut self, nt: &NoteType, usn: Option<Usn>) -> Result<()> {
        assert!(nt.id == self.ntid);
        if nt.fields.len() != self.fields.len() {
            return Err(AnkiError::invalid_input(format!(
                "note has {} fields, expected {}",
                self.fields.len(),
                nt.fields.len()
            )));
        }

        let field1_nohtml = strip_html_preserving_image_filenames(&self.fields()[0]);
        let checksum = field_checksum(field1_nohtml.as_ref());
        let sort_field = if nt.config.sort_field_idx == 0 {
            field1_nohtml
        } else {
            strip_html_preserving_image_filenames(
                self.fields
                    .get(nt.config.sort_field_idx as usize)
                    .map(AsRef::as_ref)
                    .unwrap_or(""),
            )
        };
        self.sort_field = Some(sort_field.into());
        self.checksum = Some(checksum);
        if let Some(usn) = usn {
            self.mtime = TimestampSecs::now();
            self.usn = usn;
        }
        Ok(())
    }

    pub(crate) fn nonempty_fields<'a>(&self, fields: &'a [NoteField]) -> HashSet<&'a str> {
        self.fields
            .iter()
            .enumerate()
            .filter_map(|(ord, s)| {
                if s.trim().is_empty() {
                    None
                } else {
                    fields.get(ord).map(|f| f.name.as_str())
                }
            })
            .collect()
    }
}

impl From<Note> for pb::Note {
    fn from(n: Note) -> Self {
        pb::Note {
            id: n.id.0,
            guid: n.guid,
            ntid: n.ntid.0,
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
            ntid: NoteTypeID(n.ntid),
            mtime: TimestampSecs(n.mtime_secs as i64),
            usn: Usn(n.usn),
            tags: n.tags,
            fields: n.fields,
            sort_field: None,
            checksum: None,
        }
    }
}

/// Text must be passed to strip_html_preserving_image_filenames() by
/// caller prior to passing in here.
pub(crate) fn field_checksum(text: &str) -> u32 {
    let digest = sha1::Sha1::from(text).digest().bytes();
    u32::from_be_bytes(digest[..4].try_into().unwrap())
}

fn guid() -> String {
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

impl Collection {
    fn canonify_note_tags(&self, note: &mut Note, usn: Usn) -> Result<()> {
        // fixme: avoid the excess split/join
        note.tags = self
            .canonify_tags(&note.tags.join(" "), usn)?
            .0
            .split(' ')
            .map(Into::into)
            .collect();
        Ok(())
    }

    pub fn add_note(&mut self, note: &mut Note, did: DeckID) -> Result<()> {
        self.transact(None, |col| {
            let nt = col
                .get_notetype(note.ntid)?
                .ok_or_else(|| AnkiError::invalid_input("missing note type"))?;
            let ctx = CardGenContext::new(&nt, col.usn()?);
            col.add_note_inner(&ctx, note, did)
        })
    }

    pub(crate) fn add_note_inner(
        &mut self,
        ctx: &CardGenContext,
        note: &mut Note,
        did: DeckID,
    ) -> Result<()> {
        self.canonify_note_tags(note, ctx.usn)?;
        note.prepare_for_update(&ctx.notetype, Some(ctx.usn))?;
        self.storage.add_note(note)?;
        self.generate_cards_for_new_note(ctx, note, did)
    }

    pub fn update_note(&mut self, note: &mut Note) -> Result<()> {
        self.transact(None, |col| {
            let nt = col
                .get_notetype(note.ntid)?
                .ok_or_else(|| AnkiError::invalid_input("missing note type"))?;
            let ctx = CardGenContext::new(&nt, col.usn()?);
            col.update_note_inner(&ctx, note)
        })
    }

    pub(crate) fn update_note_inner(
        &mut self,
        ctx: &CardGenContext,
        note: &mut Note,
    ) -> Result<()> {
        self.canonify_note_tags(note, ctx.usn)?;
        note.prepare_for_update(ctx.notetype, Some(ctx.usn))?;
        self.generate_cards_for_existing_note(ctx, note)?;
        self.storage.update_note(note)?;

        Ok(())
    }
}

#[cfg(test)]
mod test {
    use super::{anki_base91, field_checksum};
    use crate::{collection::open_test_collection, decks::DeckID, err::Result};

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
        ords.sort();
        assert_eq!(ords, vec![0, 1, 2, 499]);

        Ok(())
    }
}
