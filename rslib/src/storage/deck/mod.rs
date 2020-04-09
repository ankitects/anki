// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::SqliteStorage;
use crate::{
    decks::{Deck, DeckID},
    err::Result,
};
use rusqlite::NO_PARAMS;
use std::collections::HashMap;

impl SqliteStorage {
    pub(crate) fn get_all_decks(&self) -> Result<HashMap<DeckID, Deck>> {
        self.db
            .query_row_and_then("select decks from col", NO_PARAMS, |row| -> Result<_> {
                Ok(serde_json::from_str(row.get_raw(0).as_str()?)?)
            })
    }

    pub(crate) fn set_all_decks(&self, decks: HashMap<DeckID, Deck>) -> Result<()> {
        let json = serde_json::to_string(&decks)?;
        self.db.execute("update col set decks = ?", &[json])?;
        Ok(())
    }
}
