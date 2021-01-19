// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::SqliteStorage;
use crate::{err::Result, tags::Tag, types::Usn};

use rusqlite::{params, Row, NO_PARAMS};
use std::collections::HashMap;

fn row_to_tag(row: &Row) -> Result<Tag> {
    Ok(Tag {
        name: row.get(0)?,
        usn: row.get(1)?,
        collapsed: row.get(2)?,
    })
}

impl SqliteStorage {
    /// All tags in the collection, in alphabetical order.
    pub(crate) fn all_tags(&self) -> Result<Vec<Tag>> {
        self.db
            .prepare_cached("select tag, usn, collapsed from tags")?
            .query_and_then(NO_PARAMS, row_to_tag)?
            .collect()
    }

    pub(crate) fn collapsed_tags(&self) -> Result<Vec<String>> {
        self.db
            .prepare_cached("select tag from tags where collapsed = true")?
            .query_and_then(NO_PARAMS, |r| r.get::<_, String>(0).map_err(Into::into))?
            .collect::<Result<Vec<_>>>()
    }

    pub(crate) fn restore_collapsed_tags(&self, tags: &[String]) -> Result<()> {
        let mut stmt = self
            .db
            .prepare_cached("update tags set collapsed = true where tag = ?")?;
        for tag in tags {
            stmt.execute(&[tag])?;
        }
        Ok(())
    }

    pub(crate) fn get_tag(&self, name: &str) -> Result<Option<Tag>> {
        self.db
            .prepare_cached("select tag, usn, collapsed from tags where tag = ?")?
            .query_and_then(&[name], row_to_tag)?
            .next()
            .transpose()
    }

    pub(crate) fn register_tag(&self, tag: &Tag) -> Result<()> {
        self.db
            .prepare_cached(include_str!("add.sql"))?
            .execute(params![tag.name, tag.usn, tag.collapsed])?;
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

    pub(crate) fn clear_tag(&self, tag: &str) -> Result<()> {
        self.db
            .prepare_cached("delete from tags where tag regexp ?")?
            .execute(&[format!("(?i)^{}($|::)", regex::escape(tag))])?;

        Ok(())
    }

    pub(crate) fn set_tag_collapsed(&self, tag: &str, collapsed: bool) -> Result<()> {
        self.db
            .prepare_cached("update tags set collapsed = ? where tag = ?")?
            .execute(params![collapsed, tag])?;

        Ok(())
    }

    pub(crate) fn clear_tags(&self) -> Result<()> {
        self.db.execute("delete from tags", NO_PARAMS)?;
        Ok(())
    }

    pub(crate) fn clear_tag_usns(&self) -> Result<()> {
        self.db
            .execute("update tags set usn = 0 where usn != 0", NO_PARAMS)?;
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
            .query_and_then(&[usn], |r| r.get(0).map_err(Into::into))?
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
            .query_row_and_then("select tags from col", NO_PARAMS, |row| {
                let tags: Result<HashMap<String, Usn>> =
                    serde_json::from_str(row.get_raw(0).as_str()?).map_err(Into::into);
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
            .query_and_then(NO_PARAMS, |r| Ok(Tag::new(r.get(0)?, r.get(1)?)))?
            .collect::<Result<Vec<Tag>>>()?;
        self.db
            .execute_batch(include_str!["../upgrades/schema17_upgrade.sql"])?;
        tags.into_iter()
            .try_for_each(|tag| -> Result<()> { self.register_tag(&tag) })
    }
}
