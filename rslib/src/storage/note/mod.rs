// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashSet;

use crate::{
    err::Result,
    notes::{Note, NoteID},
    notetype::NoteTypeID,
    tags::{join_tags, split_tags},
    timestamp::TimestampMillis,
};
use rusqlite::{params, Row, NO_PARAMS};

pub(crate) fn split_fields(fields: &str) -> Vec<String> {
    fields.split('\x1f').map(Into::into).collect()
}

pub(crate) fn join_fields(fields: &[String]) -> String {
    fields.join("\x1f")
}

fn row_to_note(row: &Row) -> Result<Note> {
    Ok(Note {
        id: row.get(0)?,
        guid: row.get(1)?,
        notetype_id: row.get(2)?,
        mtime: row.get(3)?,
        usn: row.get(4)?,
        tags: split_tags(row.get_raw(5).as_str()?)
            .map(Into::into)
            .collect(),
        fields: split_fields(row.get_raw(6).as_str()?),
        sort_field: None,
        checksum: None,
    })
}

impl super::SqliteStorage {
    pub fn get_note(&self, nid: NoteID) -> Result<Option<Note>> {
        self.db
            .prepare_cached(concat!(include_str!("get.sql"), " where id = ?"))?
            .query_and_then(params![nid], row_to_note)?
            .next()
            .transpose()
    }

    /// Caller must call note.prepare_for_update() prior to calling this.
    pub(crate) fn update_note(&self, note: &Note) -> Result<()> {
        assert!(note.id.0 != 0);
        let mut stmt = self.db.prepare_cached(include_str!("update.sql"))?;
        stmt.execute(params![
            note.guid,
            note.notetype_id,
            note.mtime,
            note.usn,
            join_tags(&note.tags),
            join_fields(&note.fields()),
            note.sort_field.as_ref().unwrap(),
            note.checksum.unwrap(),
            note.id
        ])?;
        Ok(())
    }

    pub(crate) fn add_note(&self, note: &mut Note) -> Result<()> {
        assert!(note.id.0 == 0);
        let mut stmt = self.db.prepare_cached(include_str!("add.sql"))?;
        stmt.execute(params![
            TimestampMillis::now(),
            note.guid,
            note.notetype_id,
            note.mtime,
            note.usn,
            join_tags(&note.tags),
            join_fields(&note.fields()),
            note.sort_field.as_ref().unwrap(),
            note.checksum.unwrap(),
        ])?;
        note.id.0 = self.db.last_insert_rowid();
        Ok(())
    }

    /// Add or update the provided note, preserving ID. Used by the syncing code.
    pub(crate) fn add_or_update_note(&self, note: &Note) -> Result<()> {
        let mut stmt = self.db.prepare_cached(include_str!("add_or_update.sql"))?;
        stmt.execute(params![
            note.id,
            note.guid,
            note.notetype_id,
            note.mtime,
            note.usn,
            join_tags(&note.tags),
            join_fields(&note.fields()),
            note.sort_field.as_ref().unwrap(),
            note.checksum.unwrap(),
        ])?;
        Ok(())
    }

    pub(crate) fn remove_note(&self, nid: NoteID) -> Result<()> {
        self.db
            .prepare_cached("delete from notes where id = ?")?
            .execute(&[nid])?;
        Ok(())
    }

    pub(crate) fn note_is_orphaned(&self, nid: NoteID) -> Result<bool> {
        self.db
            .prepare_cached(include_str!("is_orphaned.sql"))?
            .query_row(&[nid], |r| r.get(0))
            .map_err(Into::into)
    }

    pub(crate) fn clear_pending_note_usns(&self) -> Result<()> {
        self.db
            .prepare("update notes set usn = 0 where usn = -1")?
            .execute(NO_PARAMS)?;
        Ok(())
    }

    pub(crate) fn fix_invalid_utf8_in_note(&self, nid: NoteID) -> Result<()> {
        self.db
            .query_row(
                "select cast(flds as blob) from notes where id=?",
                &[nid],
                |row| {
                    let fixed_flds: Vec<u8> = row.get(0)?;
                    let fixed_str = String::from_utf8_lossy(&fixed_flds);
                    self.db.execute(
                        "update notes set flds = ? where id = ?",
                        params![fixed_str, nid],
                    )
                },
            )
            .map_err(Into::into)
            .map(|_| ())
    }

    /// Returns [(nid, field 0)] of notes with the same checksum.
    /// The caller should strip the fields and compare to see if they actually
    /// match.
    pub(crate) fn note_fields_by_checksum(
        &self,
        ntid: NoteTypeID,
        csum: u32,
    ) -> Result<Vec<(NoteID, String)>> {
        self.db
            .prepare("select id, field_at_index(flds, 0) from notes where csum=? and mid=?")?
            .query_and_then(params![csum, ntid], |r| Ok((r.get(0)?, r.get(1)?)))?
            .collect()
    }

    /// Return total number of notes. Slow.
    pub(crate) fn total_notes(&self) -> Result<u32> {
        self.db
            .prepare("select count() from notes")?
            .query_row(NO_PARAMS, |r| r.get(0))
            .map_err(Into::into)
    }

    pub(crate) fn all_tags_in_notes(&self) -> Result<HashSet<String>> {
        let mut stmt = self
            .db
            .prepare_cached("select tags from notes where tags != ''")?;
        let mut query = stmt.query(NO_PARAMS)?;
        let mut seen: HashSet<String> = HashSet::new();
        while let Some(rows) = query.next()? {
            for tag in split_tags(rows.get_raw(0).as_str()?) {
                if !seen.contains(tag) {
                    seen.insert(tag.to_string());
                }
            }
        }
        Ok(seen)
    }
}
