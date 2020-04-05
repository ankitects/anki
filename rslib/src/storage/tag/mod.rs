// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::SqliteStorage;
use crate::{err::Result, types::Usn};
use rusqlite::{params, NO_PARAMS};
use std::collections::HashMap;

impl SqliteStorage {
    pub(crate) fn all_tags(&self) -> Result<Vec<(String, Usn)>> {
        self.db
            .prepare_cached("select tag, usn from tags")?
            .query_and_then(NO_PARAMS, |row| -> Result<_> {
                Ok((row.get(0)?, row.get(1)?))
            })?
            .collect()
    }

    pub(crate) fn register_tag(&self, tag: &str, usn: Usn) -> Result<()> {
        self.db
            .prepare_cached(include_str!("add.sql"))?
            .execute(params![tag, usn])?;
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
    pub(crate) fn get_changed_tags(&self, usn: Usn) -> Result<Vec<String>> {
        let tags: Vec<String> = self
            .db
            .prepare("select tag from tags where usn=-1")?
            .query_map(NO_PARAMS, |row| row.get(0))?
            .collect::<std::result::Result<_, rusqlite::Error>>()?;
        self.db
            .execute("update tags set usn=? where usn=-1", &[&usn])?;
        Ok(tags)
    }

    // Upgrading/downgrading

    pub(super) fn upgrade_tags_to_schema13(&self) -> Result<()> {
        let tags = self
            .db
            .query_row_and_then("select tags from col", NO_PARAMS, |row| {
                let tags: Result<HashMap<String, Usn>> =
                    serde_json::from_str(row.get_raw(0).as_str()?).map_err(Into::into);
                tags
            })?;
        for (tag, usn) in tags.into_iter() {
            self.register_tag(&tag, usn)?;
        }
        self.db.execute_batch("update col set tags=''")?;

        Ok(())
    }

    pub(super) fn downgrade_tags_from_schema13(&self) -> Result<()> {
        let alltags = self.all_tags()?;
        let tagsmap: HashMap<String, Usn> = alltags.into_iter().collect();
        self.db.execute(
            "update col set tags=?",
            params![serde_json::to_string(&tagsmap)?],
        )?;
        Ok(())
    }
}
