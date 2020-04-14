// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::err::{AnkiError, Result};
use crate::notetype::NoteTypeID;
use crate::text::strip_html_preserving_image_filenames;
use crate::timestamp::TimestampSecs;
use crate::{define_newtype, types::Usn};
use std::convert::TryInto;

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
    pub fields: Vec<String>,
    pub(crate) sort_field: Option<String>,
    pub(crate) checksum: Option<u32>,
}

impl Note {
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
        // hard-coded for now
        self.usn = usn;
    }
}

/// Text must be passed to strip_html_preserving_image_filenames() by
/// caller prior to passing in here.
pub(crate) fn field_checksum(text: &str) -> u32 {
    let digest = sha1::Sha1::from(text).digest().bytes();
    u32::from_be_bytes(digest[..4].try_into().unwrap())
}
