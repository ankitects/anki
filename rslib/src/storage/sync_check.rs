// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::*;
use crate::error::SyncErrorKind;
use crate::prelude::*;
use crate::sync::collection::sanity::SanityCheckCounts;
use crate::sync::collection::sanity::SanityCheckDueCounts;

impl SqliteStorage {
    fn table_has_usn(&self, table: &str) -> Result<bool> {
        Ok(self
            .db
            .prepare(&format!("select null from {table} where usn=-1"))?
            .query([])?
            .next()?
            .is_some())
    }

    fn table_count(&self, table: &str) -> Result<u32> {
        self.db
            .query_row(&format!("select count() from {table}"), [], |r| r.get(0))
            .map_err(Into::into)
    }

    pub(crate) fn sanity_check_info(&self) -> Result<SanityCheckCounts> {
        for table in &[
            "cards",
            "notes",
            "revlog",
            "graves",
            "decks",
            "deck_config",
            "tags",
            "notetypes",
        ] {
            if self.table_has_usn(table)? {
                return Err(AnkiError::sync_error(
                    format!("table had usn=-1: {table}"),
                    SyncErrorKind::Other,
                ));
            }
        }

        Ok(SanityCheckCounts {
            counts: SanityCheckDueCounts::default(),
            cards: self.table_count("cards")?,
            notes: self.table_count("notes")?,
            revlog: self.table_count("revlog")?,
            graves: self.table_count("graves")?,
            notetypes: self.table_count("notetypes")?,
            decks: self.table_count("decks")?,
            deck_config: self.table_count("deck_config")?,
        })
    }
}
