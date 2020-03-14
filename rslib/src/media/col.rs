// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/// Basic note reading/updating functionality for the media DB check.
use crate::err::{AnkiError, Result};
use crate::text::strip_html_preserving_image_filenames;
use crate::time::{i64_unix_millis, i64_unix_secs};
use crate::types::{ObjID, Timestamp, Usn};
use rusqlite::{params, Connection, Row, NO_PARAMS};
use serde_aux::field_attributes::deserialize_number_from_string;
use serde_derive::Deserialize;
use std::collections::HashMap;
use std::convert::TryInto;
use std::path::Path;

#[derive(Debug)]
pub(super) struct Note {
    pub id: ObjID,
    pub mid: ObjID,
    pub mtime_secs: Timestamp,
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

fn field_checksum(text: &str) -> u32 {
    let digest = sha1::Sha1::from(text).digest().bytes();
    u32::from_be_bytes(digest[..4].try_into().unwrap())
}

pub(super) fn open_or_create_collection_db(path: &Path) -> Result<Connection> {
    let db = Connection::open(path)?;

    db.pragma_update(None, "locking_mode", &"exclusive")?;
    db.pragma_update(None, "page_size", &4096)?;
    db.pragma_update(None, "cache_size", &(-40 * 1024))?;
    db.pragma_update(None, "legacy_file_format", &false)?;
    db.pragma_update(None, "journal", &"wal")?;
    db.set_prepared_statement_cache_capacity(5);

    Ok(db)
}

#[derive(Deserialize, Debug)]
pub(super) struct NoteType {
    #[serde(deserialize_with = "deserialize_number_from_string")]
    id: ObjID,
    #[serde(rename = "sortf")]
    sort_field_idx: u16,

    #[serde(rename = "latexsvg", default)]
    latex_svg: bool,
}

impl NoteType {
    pub fn latex_uses_svg(&self) -> bool {
        self.latex_svg
    }
}

pub(super) fn get_note_types(db: &Connection) -> Result<HashMap<ObjID, NoteType>> {
    let mut stmt = db.prepare("select models from col")?;
    let note_types = stmt
        .query_and_then(NO_PARAMS, |row| -> Result<HashMap<ObjID, NoteType>> {
            let v: HashMap<ObjID, NoteType> = serde_json::from_str(row.get_raw(0).as_str()?)?;
            Ok(v)
        })?
        .next()
        .ok_or_else(|| AnkiError::DBError {
            info: "col table empty".to_string(),
        })??;
    Ok(note_types)
}

#[allow(dead_code)]
fn get_note(db: &Connection, nid: ObjID) -> Result<Option<Note>> {
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
        mid: row.get(1)?,
        mtime_secs: row.get(2)?,
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
    note.mtime_secs = i64_unix_secs();
    // hard-coded for now
    note.usn = -1;
    let csum = field_checksum(&note.fields()[0]);
    let sort_field = strip_html_preserving_image_filenames(
        note.fields()
            .get(note_type.sort_field_idx as usize)
            .ok_or_else(|| AnkiError::DBError {
                info: "sort field out of range".to_string(),
            })?,
    );

    let mut stmt =
        db.prepare_cached("update notes set mod=?,usn=?,flds=?,sfld=?,csum=? where id=?")?;
    stmt.execute(params![
        note.mtime_secs,
        note.usn,
        note.fields().join("\x1f"),
        sort_field,
        csum,
        note.id,
    ])?;

    Ok(())
}

pub(super) fn mark_collection_modified(db: &Connection) -> Result<()> {
    db.execute("update col set mod=?", params![i64_unix_millis()])?;
    Ok(())
}
