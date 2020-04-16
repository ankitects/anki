// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::err::{AnkiError, Result};
use crate::notetype::NoteTypeID;
use crate::text::strip_html_preserving_image_filenames;
use crate::timestamp::TimestampSecs;
use crate::{collection::Collection, define_newtype, types::Usn};
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
    pub(crate) fn new(ntid: NoteTypeID, field_count: usize) -> Self {
        Note {
            id: NoteID(0),
            guid: guid(),
            ntid,
            mtime: TimestampSecs(0),
            usn: Usn(0),
            tags: vec![],
            fields: vec!["".to_string(); field_count],
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

    pub fn prepare_for_update(&mut self, sort_field_idx: usize, usn: Usn) {
        let field1_nohtml = strip_html_preserving_image_filenames(&self.fields()[0]);
        let checksum = field_checksum(field1_nohtml.as_ref());
        let sort_field = if sort_field_idx == 0 {
            field1_nohtml
        } else {
            strip_html_preserving_image_filenames(
                self.fields
                    .get(sort_field_idx)
                    .map(AsRef::as_ref)
                    .unwrap_or(""),
            )
        };
        self.sort_field = Some(sort_field.into());
        self.checksum = Some(checksum);
        self.mtime = TimestampSecs::now();
        self.usn = usn;
    }

    #[allow(dead_code)]
    pub(crate) fn nonempty_fields(&self) -> HashSet<u16> {
        self.fields
            .iter()
            .enumerate()
            .filter_map(|(ord, s)| {
                if s.trim().is_empty() {
                    None
                } else {
                    Some(ord as u16)
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
            println!("fixme: need to add cards, abort if no cards generated, etc");
            // fixme: proper index
            note.prepare_for_update(0, col.usn()?);
            col.storage.add_note(note)
        })
    }
}

#[cfg(test)]
mod test {
    use super::{anki_base91, field_checksum};

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
}
