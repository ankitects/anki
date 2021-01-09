// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::SqliteStorage;
use crate::{
    err::Result,
    tags::{human_tag_name_to_native, native_tag_name_to_human, Tag, TagConfig, TagID},
    timestamp::TimestampMillis,
    types::Usn,
};
use prost::Message;
use rusqlite::{params, Row, NO_PARAMS};
use std::collections::HashMap;

fn row_to_tag(row: &Row) -> Result<Tag> {
    let config = TagConfig::decode(row.get_raw(3).as_blob()?)?;
    Ok(Tag {
        id: row.get(0)?,
        name: row.get(1)?,
        usn: row.get(2)?,
        config,
    })
}

impl SqliteStorage {
    pub(crate) fn all_tags(&self) -> Result<Vec<Tag>> {
        self.db
            .prepare_cached("select id, name, usn, config from tags")?
            .query_and_then(NO_PARAMS, row_to_tag)?
            .collect()
    }

    /// Get all tags in human form, sorted by name
    pub(crate) fn all_tags_sorted(&self) -> Result<Vec<Tag>> {
        self.db
            .prepare_cached("select id, name, usn, config from tags order by name")?
            .query_and_then(NO_PARAMS, |row| {
                let mut tag = row_to_tag(row)?;
                tag.name = native_tag_name_to_human(&tag.name);
                Ok(tag)
            })?
            .collect()
    }

    /// Get tag by human name
    pub(crate) fn get_tag(&self, name: &str) -> Result<Option<Tag>> {
        self.db
            .prepare_cached("select id, name, usn, config from tags where name = ?")?
            .query_and_then(&[human_tag_name_to_native(name)], |row| {
                let mut tag = row_to_tag(row)?;
                tag.name = native_tag_name_to_human(&tag.name);
                Ok(tag)
            })?
            .next()
            .transpose()
    }

    fn alloc_id(&self) -> rusqlite::Result<TagID> {
        self.db
            .prepare_cached(include_str!("alloc_id.sql"))?
            .query_row(&[TimestampMillis::now()], |r| r.get(0))
    }

    pub(crate) fn register_tag(&self, tag: &mut Tag) -> Result<()> {
        let mut config = vec![];
        tag.config.encode(&mut config)?;
        tag.id = self.alloc_id()?;
        self.update_tag(tag)?;
        Ok(())
    }

    pub(crate) fn update_tag(&self, tag: &Tag) -> Result<()> {
        let mut config = vec![];
        tag.config.encode(&mut config)?;
        self.db
            .prepare_cached(include_str!("add.sql"))?
            .execute(params![tag.id, tag.name, tag.usn, config])?;
        Ok(())
    }

    pub(crate) fn preferred_tag_case(&self, tag: &str) -> Result<Option<String>> {
        self.db
            .prepare_cached("select name from tags where name = ?")?
            .query_and_then(params![tag], |row| row.get(0))?
            .next()
            .transpose()
            .map_err(Into::into)
    }

    pub(crate) fn clear_tag(&self, tag: &str) -> Result<()> {
        self.db
            .prepare_cached("delete from tags where name regexp ?")?
            .execute(&[format!("^{}($|\x1f)", regex::escape(tag))])?;

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
                "select name from tags where {}",
                usn.pending_object_clause()
            ))?
            .query_and_then(&[usn], |r| r.get(0).map_err(Into::into))?
            .collect()
    }

    pub(crate) fn update_tag_usns(&self, tags: &[String], new_usn: Usn) -> Result<()> {
        let mut stmt = self
            .db
            .prepare_cached("update tags set usn=? where name=?")?;
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
            .query_and_then(NO_PARAMS, |r| {
                Ok(Tag {
                    name: r.get(0)?,
                    usn: r.get(1)?,
                    ..Default::default()
                })
            })?
            .collect::<Result<Vec<Tag>>>()?;
        self.db.execute_batch(
            "
        drop table tags;
        create table tags (
          id integer primary key not null,
          name text not null collate unicase,
          usn integer not null,
          config blob not null
        );
        ",
        )?;
        tags.into_iter().try_for_each(|mut tag| -> Result<()> {
            tag.name = human_tag_name_to_native(&tag.name);
            self.register_tag(&mut tag)
        })
    }

    pub(super) fn downgrade_tags_from_schema17(&self) -> Result<()> {
        let tags = self.all_tags()?;
        self.clear_tags()?;
        tags.into_iter().try_for_each(|mut tag| -> Result<()> {
            tag.name = native_tag_name_to_human(&tag.name);
            self.register_tag(&mut tag)
        })
    }
}
