// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::SqliteStorage;
use crate::{
    deckconf::{DeckConf, DeckConfID},
    err::{AnkiError, Result},
};
use rusqlite::{params, NO_PARAMS};
use std::collections::HashMap;

impl SqliteStorage {
    pub(crate) fn all_deck_conf(&self) -> Result<HashMap<DeckConfID, DeckConf>> {
        self.db
            .prepare_cached("select dconf from col")?
            .query_and_then(NO_PARAMS, |row| -> Result<_> {
                Ok(serde_json::from_str(row.get_raw(0).as_str()?)?)
            })?
            .next()
            .ok_or_else(|| AnkiError::invalid_input("no col table"))?
    }

    pub(crate) fn flush_deck_conf(&self, conf: &HashMap<DeckConfID, DeckConf>) -> Result<()> {
        self.db
            .prepare_cached("update col set dconf = ?")?
            .execute(params![&serde_json::to_string(conf)?])?;
        Ok(())
    }
}
