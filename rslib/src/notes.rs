// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/// At the moment, this is just basic note reading/updating functionality for
/// the media DB check.
use crate::err::{AnkiError, DBErrorKind, Result};
use crate::notetype::NoteTypeID;
use crate::text::strip_html_preserving_image_filenames;
use crate::timestamp::TimestampSecs;
use crate::{define_newtype, notetype::NoteType, types::Usn};
use rusqlite::{params, Connection, Row, NO_PARAMS};
use std::convert::TryInto;

define_newtype!(NoteID, i64);

#[derive(Debug)]
pub(super) struct Note {
    pub id: NoteID,
    pub ntid: NoteTypeID,
    pub mtime: TimestampSecs,
    pub usn: Usn,
    fields: Vec<String>,
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
}

/// Text must be passed to strip_html_preserving_image_filenames() by
/// caller prior to passing in here.
pub(crate) fn field_checksum(text: &str) -> u32 {
    let digest = sha1::Sha1::from(text).digest().bytes();
    u32::from_be_bytes(digest[..4].try_into().unwrap())
}

#[allow(dead_code)]
fn get_note(db: &Connection, nid: NoteID) -> Result<Option<Note>> {
    let mut stmt = db.prepare_cached("select id, mid, mod, usn, flds from notes where id=?")?;
    let note = stmt.query_and_then(params![nid], row_to_note)?.next();

    note.transpose()
}

pub(super) fn for_every_note<F: FnMut(&mut Note) -> Result<()>>(
    db: &Connection,
    mut func: F,
) -> Result<()> {
    let mut stmt = db.prepare("select id, mid, mod, usn, flds from notes")?;
    for result in stmt.query_and_then(NO_PARAMS, |row| {
        let mut note = row_to_note(row)?;
        func(&mut note)
    })? {
        result?;
    }
    Ok(())
}

fn row_to_note(row: &Row) -> Result<Note> {
    Ok(Note {
        id: row.get(0)?,
        ntid: row.get(1)?,
        mtime: row.get(2)?,
        usn: row.get(3)?,
        fields: row
            .get_raw(4)
            .as_str()?
            .split('\x1f')
            .map(|s| s.to_string())
            .collect(),
    })
}

pub(super) fn set_note(db: &Connection, note: &mut Note, note_type: &NoteType) -> Result<()> {
    note.mtime = TimestampSecs::now();
    // hard-coded for now
    note.usn = Usn(-1);
    let field1_nohtml = strip_html_preserving_image_filenames(&note.fields()[0]);
    let csum = field_checksum(field1_nohtml.as_ref());
    let sort_field = if note_type.sort_field_idx == 0 {
        field1_nohtml
    } else {
        strip_html_preserving_image_filenames(
            note.fields()
                .get(note_type.sort_field_idx as usize)
                .ok_or_else(|| AnkiError::DBError {
                    info: "sort field out of range".to_string(),
                    kind: DBErrorKind::MissingEntity,
                })?,
        )
    };

    let mut stmt =
        db.prepare_cached("update notes set mod=?,usn=?,flds=?,sfld=?,csum=? where id=?")?;
    stmt.execute(params![
        note.mtime,
        note.usn,
        note.fields().join("\x1f"),
        sort_field,
        csum,
        note.id,
    ])?;

    Ok(())
}
