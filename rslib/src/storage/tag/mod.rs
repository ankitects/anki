// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use rusqlite::params;
use rusqlite::Row;

use super::SqliteStorage;
use crate::error::Result;
use crate::tags::Tag;
use crate::types::Usn;

fn row_to_tag(row: &Row) -> Result<Tag> {
    Ok(Tag {
        name: row.get(0)?,
        usn: row.get(1)?,
        expanded: !row.get(2)?,
    })
}

impl SqliteStorage {
    /// All tags in the collection, in alphabetical order.
    pub fn all_tags(&self) -> Result<Vec<Tag>> {
        self.db
            .prepare_cached(include_str!("get.sql"))?
            .query_and_then([], row_to_tag)?
            .collect()
    }

    pub(crate) fn expanded_tags(&self) -> Result<Vec<String>> {
        self.db
            .prepare_cached("select tag from tags where collapsed = false")?
            .query_and_then([], |r| r.get::<_, String>(0).map_err(Into::into))?
            .collect::<Result<Vec<_>>>()
    }

    pub(crate) fn restore_expanded_tags(&self, tags: &[String]) -> Result<()> {
        let mut stmt = self
            .db
            .prepare_cached("update tags set collapsed = false where tag = ?")?;
        for tag in tags {
            stmt.execute([tag])?;
        }
        Ok(())
    }

    pub(crate) fn get_tag(&self, name: &str) -> Result<Option<Tag>> {
        self.db
            .prepare_cached(&format!("{} where tag = ?", include_str!("get.sql")))?
            .query_and_then([name], row_to_tag)?
            .next()
            .transpose()
    }

    pub(crate) fn register_tag(&self, tag: &Tag) -> Result<()> {
        self.db
            .prepare_cached(include_str!("add.sql"))?
            .execute(params![tag.name, tag.usn, !tag.expanded])?;
        Ok(())
    }

    pub(crate) fn preferred_tag_case(&self, tag: &str) -> Result<Option<String>> {
        self.db
            .prepare_cached("select tag from tags where tag = ?")?
            .query_and_then(params![tag], |row| row.get(0))?
            .next()
            .transpose()
            .map_err(Into::into)
    }

    pub(crate) fn get_tags_by_predicate<F>(&self, mut want: F) -> Result<Vec<Tag>>
    where
        F: FnMut(&str) -> bool,
    {
        let mut query_stmt = self.db.prepare_cached(include_str!("get.sql"))?;
        let mut rows = query_stmt.query([])?;
        let mut output = vec![];
        while let Some(row) = rows.next()? {
            let tag = row.get_ref_unwrap(0).as_str()?;
            if want(tag) {
                output.push(Tag {
                    name: tag.to_owned(),
                    usn: row.get(1)?,
                    expanded: !row.get(2)?,
                })
            }
        }
        Ok(output)
    }

    pub(crate) fn remove_single_tag(&self, tag: &str) -> Result<()> {
        self.db
            .prepare_cached("delete from tags where tag = ?")?
            .execute([tag])?;

        Ok(())
    }

    pub(crate) fn update_tag(&self, tag: &Tag) -> Result<()> {
        self.db
            .prepare_cached(include_str!("update.sql"))?
            .execute(params![&tag.name, tag.usn, !tag.expanded])?;
        Ok(())
    }

    pub(crate) fn clear_all_tags(&self) -> Result<()> {
        self.db.execute("delete from tags", [])?;
        Ok(())
    }

    pub(crate) fn clear_tag_usns(&self) -> Result<()> {
        self.db
            .execute("update tags set usn = 0 where usn != 0", [])?;
        Ok(())
    }

    // fixme: in the future we could just register tags as part of the sync
    // instead of sending the tag list separately
    pub(crate) fn tags_pending_sync(&self, usn: Usn) -> Result<Vec<String>> {
        self.db
            .prepare_cached(&format!(
                "select tag from tags where {}",
                usn.pending_object_clause()
            ))?
            .query_and_then([usn], |r| r.get(0).map_err(Into::into))?
            .collect()
    }

    pub(crate) fn update_tag_usns(&self, tags: &[String], new_usn: Usn) -> Result<()> {
        let mut stmt = self
            .db
            .prepare_cached("update tags set usn=? where tag=?")?;
        for tag in tags {
            stmt.execute(params![new_usn, tag])?;
        }
        Ok(())
    }

    // Upgrading/downgrading

    pub(super) fn upgrade_tags_to_schema14(&self) -> Result<()> {
        let tags = self
            .db
            .query_row_and_then("select tags from col", [], |row| {
                let tags: Result<HashMap<String, Usn>> =
                    serde_json::from_str(row.get_ref_unwrap(0).as_str()?).map_err(Into::into);
                tags
            })?;
        let mut stmt = self
            .db
            .prepare_cached("insert or ignore into tags (tag, usn) values (?, ?)")?;
        for (tag, usn) in tags.into_iter() {
            stmt.execute(params![tag, usn])?;
        }
        self.db.execute_batch("update col set tags=''")?;

        Ok(())
    }

    pub(super) fn downgrade_tags_from_schema14(&self) -> Result<()> {
        let alltags = self.all_tags()?;
        let tagsmap: HashMap<String, Usn> = alltags.into_iter().map(|t| (t.name, t.usn)).collect();
        self.db.execute(
            "update col set tags=?",
            params![serde_json::to_string(&tagsmap)?],
        )?;
        Ok(())
    }

    pub(super) fn upgrade_tags_to_schema17(&self) -> Result<()> {
        let tags = self
            .db
            .prepare_cached("select tag, usn from tags")?
            .query_and_then([], |r| Ok(Tag::new(r.get(0)?, r.get(1)?)))?
            .collect::<Result<Vec<Tag>>>()?;
        self.db
            .execute_batch(include_str!["../upgrades/schema17_upgrade.sql"])?;
        tags.into_iter()
            .try_for_each(|tag| -> Result<()> { self.register_tag(&tag) })
    }
}
