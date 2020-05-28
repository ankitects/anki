// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    err::Result,
    notes::{Note, NoteID},
    notetype::NoteTypeID,
    tags::{join_tags, split_tags},
    timestamp::TimestampMillis,
};
use rusqlite::{params, OptionalExtension, NO_PARAMS};

fn split_fields(fields: &str) -> Vec<String> {
    fields.split('\x1f').map(Into::into).collect()
}

fn join_fields(fields: &[String]) -> String {
    fields.join("\x1f")
}

impl super::SqliteStorage {
    pub fn get_note(&self, nid: NoteID) -> Result<Option<Note>> {
        let mut stmt = self.db.prepare_cached(include_str!("get.sql"))?;
        stmt.query_row(params![nid], |row| {
            Ok(Note {
                id: nid,
                guid: row.get(0)?,
                ntid: row.get(1)?,
                mtime: row.get(2)?,
                usn: row.get(3)?,
                tags: split_tags(row.get_raw(4).as_str()?)
                    .map(Into::into)
                    .collect(),
                fields: split_fields(row.get_raw(5).as_str()?),
                sort_field: None,
                checksum: None,
            })
        })
        .optional()
        .map_err(Into::into)
    }

    /// Caller must call note.prepare_for_update() prior to calling this.
    pub(crate) fn update_note(&self, note: &Note) -> Result<()> {
        assert!(note.id.0 != 0);
        let mut stmt = self.db.prepare_cached(include_str!("update.sql"))?;
        stmt.execute(params![
            note.guid,
            note.ntid,
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
            note.ntid,
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

    /// Returns the first field of other notes with the same checksum.
    /// The field of the provided note ID is not returned.
    pub(crate) fn note_fields_by_checksum(
        &self,
        nid: NoteID,
        ntid: NoteTypeID,
        csum: u32,
    ) -> Result<Vec<String>> {
        self.db
            .prepare("select field_at_index(flds, 0) from notes where csum=? and mid=? and id !=?")?
            .query_and_then(params![csum, ntid, nid], |r| r.get(0).map_err(Into::into))?
            .collect()
    }
}
