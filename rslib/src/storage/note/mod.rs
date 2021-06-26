// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashSet;

use rusqlite::{params, Row};

use crate::{
    error::Result,
    notes::{Note, NoteId, NoteTags},
    notetype::NotetypeId,
    tags::{join_tags, split_tags},
    timestamp::TimestampMillis,
};

pub(crate) fn split_fields(fields: &str) -> Vec<String> {
    fields.split('\x1f').map(Into::into).collect()
}

pub(crate) fn join_fields(fields: &[String]) -> String {
    fields.join("\x1f")
}

impl super::SqliteStorage {
    pub fn get_note(&self, nid: NoteId) -> Result<Option<Note>> {
        self.db
            .prepare_cached(concat!(include_str!("get.sql"), " where id = ?"))?
            .query_and_then(params![nid], row_to_note)?
            .next()
            .transpose()
    }

    pub fn get_note_without_fields(&self, nid: NoteId) -> Result<Option<Note>> {
        self.db
            .prepare_cached(concat!(
                include_str!("get_without_fields.sql"),
                " where id = ?"
            ))?
            .query_and_then(params![nid], row_to_note)?
            .next()
            .transpose()
    }

    /// If fields have been modified, caller must call note.prepare_for_update() prior to calling this.
    pub(crate) fn update_note(&self, note: &Note) -> Result<()> {
        assert!(note.id.0 != 0);
        let mut stmt = self.db.prepare_cached(include_str!("update.sql"))?;
        stmt.execute(params![
            note.guid,
            note.notetype_id,
            note.mtime,
            note.usn,
            join_tags(&note.tags),
            join_fields(note.fields()),
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
            join_fields(note.fields()),
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
            join_fields(note.fields()),
            note.sort_field.as_ref().unwrap(),
            note.checksum.unwrap(),
        ])?;
        Ok(())
    }

    pub(crate) fn remove_note(&self, nid: NoteId) -> Result<()> {
        self.db
            .prepare_cached("delete from notes where id = ?")?
            .execute([nid])?;
        Ok(())
    }

    pub(crate) fn note_is_orphaned(&self, nid: NoteId) -> Result<bool> {
        self.db
            .prepare_cached(include_str!("is_orphaned.sql"))?
            .query_row([nid], |r| r.get(0))
            .map_err(Into::into)
    }

    pub(crate) fn clear_pending_note_usns(&self) -> Result<()> {
        self.db
            .prepare("update notes set usn = 0 where usn = -1")?
            .execute([])?;
        Ok(())
    }

    pub(crate) fn fix_invalid_utf8_in_note(&self, nid: NoteId) -> Result<()> {
        self.db
            .query_row(
                "select cast(flds as blob) from notes where id=?",
                [nid],
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
        ntid: NotetypeId,
        csum: u32,
    ) -> Result<Vec<(NoteId, String)>> {
        self.db
            .prepare("select id, field_at_index(flds, 0) from notes where csum=? and mid=?")?
            .query_and_then(params![csum, ntid], |r| Ok((r.get(0)?, r.get(1)?)))?
            .collect()
    }

    /// Return total number of notes. Slow.
    pub(crate) fn total_notes(&self) -> Result<u32> {
        self.db
            .prepare("select count() from notes")?
            .query_row([], |r| r.get(0))
            .map_err(Into::into)
    }

    pub(crate) fn all_tags_in_notes(&self) -> Result<HashSet<String>> {
        let mut stmt = self
            .db
            .prepare_cached("select tags from notes where tags != ''")?;
        let mut query = stmt.query([])?;
        let mut seen: HashSet<String> = HashSet::new();
        while let Some(rows) = query.next()? {
            for tag in split_tags(rows.get_ref_unwrap(0).as_str()?) {
                if !seen.contains(tag) {
                    seen.insert(tag.to_string());
                }
            }
        }
        Ok(seen)
    }

    pub(crate) fn get_note_tags_by_id(&mut self, note_id: NoteId) -> Result<Option<NoteTags>> {
        self.db
            .prepare_cached(&format!("{} where id = ?", include_str!("get_tags.sql")))?
            .query_and_then([note_id], row_to_note_tags)?
            .next()
            .transpose()
    }

    pub(crate) fn get_note_tags_by_id_list(
        &mut self,
        note_ids: &[NoteId],
    ) -> Result<Vec<NoteTags>> {
        self.set_search_table_to_note_ids(note_ids)?;
        let out = self
            .db
            .prepare_cached(&format!(
                "{} where id in (select nid from search_nids)",
                include_str!("get_tags.sql")
            ))?
            .query_and_then([], row_to_note_tags)?
            .collect::<Result<Vec<_>>>()?;
        self.clear_searched_notes_table()?;
        Ok(out)
    }

    pub(crate) fn get_note_tags_by_predicate<F>(&mut self, want: F) -> Result<Vec<NoteTags>>
    where
        F: Fn(&str) -> bool,
    {
        let mut query_stmt = self.db.prepare_cached(include_str!("get_tags.sql"))?;
        let mut rows = query_stmt.query([])?;
        let mut output = vec![];
        while let Some(row) = rows.next()? {
            let tags = row.get_ref_unwrap(3).as_str()?;
            if want(tags) {
                output.push(row_to_note_tags(row)?)
            }
        }
        Ok(output)
    }

    pub(crate) fn update_note_tags(&mut self, note: &NoteTags) -> Result<()> {
        self.db
            .prepare_cached(include_str!("update_tags.sql"))?
            .execute(params![note.mtime, note.usn, note.tags, note.id])?;
        Ok(())
    }

    fn setup_searched_notes_table(&self) -> Result<()> {
        self.db
            .execute_batch(include_str!("search_nids_setup.sql"))?;
        Ok(())
    }

    pub(crate) fn clear_searched_notes_table(&self) -> Result<()> {
        self.db.execute("drop table if exists search_nids", [])?;
        Ok(())
    }

    /// Injects the provided card IDs into the search_nids table, for
    /// when ids have arrived outside of a search.
    /// Clear with clear_searched_notes_table().
    /// WARNING: the column name is nid, not id.
    pub(crate) fn set_search_table_to_note_ids(&mut self, notes: &[NoteId]) -> Result<()> {
        self.setup_searched_notes_table()?;
        let mut stmt = self
            .db
            .prepare_cached("insert into search_nids values (?)")?;
        for nid in notes {
            stmt.execute([nid])?;
        }

        Ok(())
    }
}

fn row_to_note(row: &Row) -> Result<Note> {
    Ok(Note::new_from_storage(
        row.get(0)?,
        row.get(1)?,
        row.get(2)?,
        row.get(3)?,
        row.get(4)?,
        split_tags(row.get_ref_unwrap(5).as_str()?)
            .map(Into::into)
            .collect(),
        split_fields(row.get_ref_unwrap(6).as_str()?),
        Some(row.get(7)?),
        Some(row.get(8).unwrap_or_default()),
    ))
}

fn row_to_note_tags(row: &Row) -> Result<NoteTags> {
    Ok(NoteTags {
        id: row.get(0)?,
        mtime: row.get(1)?,
        usn: row.get(2)?,
        tags: row.get(3)?,
    })
}
