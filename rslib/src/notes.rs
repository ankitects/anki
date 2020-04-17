// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    card::Card,
    collection::Collection,
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

    pub fn prepare_for_update(&mut self, nt: &NoteType, usn: Usn) -> Result<()> {
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
        self.mtime = TimestampSecs::now();
        self.usn = usn;
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
    pub fn add_note(&mut self, note: &mut Note) -> Result<()> {
        self.transact(None, |col| {
            let nt = col
                .storage
                .get_full_notetype(note.ntid)?
                .ok_or_else(|| AnkiError::invalid_input("missing note type"))?;

            let cardgen = CardGenContext::new(&nt, col.usn()?);
            col.add_note_inner(note, &cardgen)
        })
    }

    pub(crate) fn add_note_inner(
        &mut self,
        note: &mut Note,
        cardgen: &CardGenContext,
    ) -> Result<()> {
        let nt = cardgen.notetype;
        note.prepare_for_update(nt, cardgen.usn)?;
        let nonempty_fields = note.nonempty_fields(&cardgen.notetype.fields);
        let cards =
            cardgen.new_cards_required(&nonempty_fields, nt.target_deck_id(), &Default::default());
        if cards.is_empty() {
            return Err(AnkiError::NoCardsGenerated);
        }

        // add the note
        self.storage.add_note(note)?;
        // and its associated cards
        for (card_ord, target_deck_id) in cards {
            let mut card = Card::new(note.id, card_ord as u16, target_deck_id);
            self.add_card(&mut card)?;
        }

        Ok(())
    }
}

#[cfg(test)]
mod test {
    use super::{anki_base91, field_checksum};
    use crate::{
        collection::open_test_collection,
        err::{AnkiError, Result},
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
    fn adding() -> Result<()> {
        let mut col = open_test_collection();
        let nt = col.get_notetype_by_name("basic")?.unwrap();

        let mut note = nt.new_note();
        assert_eq!(col.add_note(&mut note), Err(AnkiError::NoCardsGenerated));

        note.fields[1] = "foo".into();
        assert_eq!(col.add_note(&mut note), Err(AnkiError::NoCardsGenerated));

        note.fields[0] = "bar".into();
        col.add_note(&mut note).unwrap();

        assert_eq!(
            col.search_cards(&format!("nid:{}", note.id), SortMode::NoOrder)
                .unwrap()
                .len(),
            1
        );

        // fixme: add nt cache, refcount
        Ok(())
    }
}
